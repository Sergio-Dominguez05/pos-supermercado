package gt.supermercado.pos.ventas.infraestructura;

import gt.supermercado.pos.impuestos.dominio.LineaCalculada;
import gt.supermercado.pos.impuestos.dominio.Totales;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
class RepositorioVentasJdbc implements RepositorioVentas {

    private static final String SELECT_VENTA = """
            SELECT v.id, v.numero, v.turno_id, v.caja_id, c.nombre AS caja_nombre, v.usuario_id, v.estado, v.tasa_iva,
                   v.creada_en, v.suspendida_en
            FROM venta v
            JOIN caja c ON c.id = v.caja_id
            """;

    /**
     * Solo las líneas vigentes: una línea eliminada (HU-06.07) ya no es parte de la venta ni de sus totales. Cada
     * consulta agrega sus condiciones con {@code AND}.
     */
    private static final String SELECT_LINEA = """
            SELECT l.id, l.producto_id, p.codigo_barras, l.descripcion, l.cantidad, l.precio_unitario,
                   l.subtotal_con_iva, l.base_imponible, l.iva
            FROM venta_linea l
            JOIN producto p ON p.id = l.producto_id
            WHERE l.eliminada_en IS NULL
            """;

    private static final RowMapper<LineaVenta> LINEA = (rs, fila) -> new LineaVenta(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getString("codigo_barras"),
            rs.getString("descripcion"),
            rs.getInt("cantidad"),
            rs.getBigDecimal("precio_unitario"),
            rs.getBigDecimal("subtotal_con_iva"),
            rs.getBigDecimal("base_imponible"),
            rs.getBigDecimal("iva"));

    private final JdbcTemplate jdbc;

    RepositorioVentasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Venta> enCursoDelTurno(long turnoId) {
        return unaVenta(SELECT_VENTA + "WHERE v.turno_id = ? AND v.estado = 'EN_CURSO'", turnoId);
    }

    @Override
    public boolean corregirCliente(long ventaId, Long clienteId) {
        return jdbc.update("UPDATE venta SET cliente_id = ? WHERE id = ? AND estado = 'FINALIZADA'", clienteId,
                ventaId) == 1;
    }

    @Override
    public boolean asignarCliente(long ventaId, Long clienteId) {
        return jdbc.update("UPDATE venta SET cliente_id = ? WHERE id = ? AND estado = 'EN_CURSO'", clienteId, ventaId) == 1;
    }

    @Override
    public Optional<Venta> buscarPorId(long id) {
        return unaVenta(SELECT_VENTA + "WHERE v.id = ?", id);
    }

    @Override
    public long iniciar(long turnoId, long cajaId, long usuarioId, BigDecimal tasaIva) {
        // Un INSERT consume un número de la secuencia aunque choque con otra venta en curso, y la venta en curso se pide
        // en cada escaneo. Para que los números no salten (HU-06.01 crit. 4), las peticiones del mismo turno se
        // ordenan bloqueando la fila del turno, y solo se inserta si de verdad no hay venta en curso.
        jdbc.queryForList("SELECT id FROM turno WHERE id = ? FOR UPDATE", Long.class, turnoId);
        Optional<Long> enCurso = idEnCurso(turnoId);
        if (enCurso.isPresent()) {
            return enCurso.get();
        }
        return jdbc.queryForObject("""
                INSERT INTO venta (turno_id, caja_id, usuario_id, tasa_iva) VALUES (?, ?, ?, ?) RETURNING id""",
                Long.class, turnoId, cajaId, usuarioId, tasaIva);
    }

    private Optional<Long> idEnCurso(long turnoId) {
        return jdbc.queryForList("SELECT id FROM venta WHERE turno_id = ? AND estado = 'EN_CURSO'", Long.class, turnoId)
                .stream().findFirst();
    }

    @Override
    public boolean cancelar(long ventaId) {
        return jdbc.update("UPDATE venta SET estado = 'CANCELADA' WHERE id = ? AND estado = 'EN_CURSO'", ventaId) == 1;
    }

    @Override
    public boolean finalizar(long ventaId, Totales totales) {
        return jdbc.update("""
                UPDATE venta SET estado = 'FINALIZADA', finalizada_en = now(), subtotal = ?, iva = ?, total = ?
                WHERE id = ? AND estado = 'EN_CURSO'""", totales.subtotal(), totales.iva(), totales.total(),
                ventaId) == 1;
    }

    @Override
    public boolean suspender(long ventaId) {
        return jdbc.update("""
                UPDATE venta SET estado = 'SUSPENDIDA', suspendida_en = now()
                WHERE id = ? AND estado = 'EN_CURSO'""", ventaId) == 1;
    }

    @Override
    public List<Venta> suspendidasDeLaCaja(long cajaId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM venta WHERE caja_id = ? AND estado = 'SUSPENDIDA' ORDER BY suspendida_en, id""",
                Long.class, cajaId);
        return ids.stream().map(id -> buscarPorId(id).orElseThrow()).toList();
    }

    @Override
    public void bloquearTurno(long turnoId) {
        jdbc.queryForList("SELECT id FROM turno WHERE id = ? FOR UPDATE", Long.class, turnoId);
    }

    @Override
    public boolean retomar(long ventaId, long cajaId, long turnoId, long usuarioId) {
        return jdbc.update("""
                UPDATE venta SET estado = 'EN_CURSO', turno_id = ?, usuario_id = ?
                WHERE id = ? AND caja_id = ? AND estado = 'SUSPENDIDA'""", turnoId, usuarioId, ventaId, cajaId) == 1;
    }

    @Override
    public int suspendidasDelTurno(long turnoId) {
        return jdbc.queryForObject("SELECT count(*) FROM venta WHERE turno_id = ? AND estado = 'SUSPENDIDA'",
                Integer.class, turnoId);
    }

    @Override
    public boolean tieneVentaConProductos(long usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM venta v
                               WHERE v.usuario_id = ? AND v.estado = 'EN_CURSO'
                                 AND EXISTS (SELECT 1 FROM venta_linea l WHERE l.venta_id = v.id AND l.eliminada_en IS NULL))""",
                Boolean.class, usuarioId));
    }

    @Override
    public boolean bloquearEnCurso(long ventaId) {
        return !jdbc.queryForList("SELECT id FROM venta WHERE id = ? AND estado = 'EN_CURSO' FOR UPDATE", Long.class,
                ventaId).isEmpty();
    }

    @Override
    public Optional<LineaVenta> lineaDelProducto(long ventaId, long productoId, BigDecimal precioUnitario) {
        return jdbc.query(SELECT_LINEA + "AND l.venta_id = ? AND l.producto_id = ? AND l.precio_unitario = ?", LINEA,
                ventaId, productoId, precioUnitario).stream().findFirst();
    }

    @Override
    public Optional<LineaVenta> lineaDeVenta(long ventaId, long lineaId) {
        return jdbc.query(SELECT_LINEA + "AND l.venta_id = ? AND l.id = ?", LINEA, ventaId, lineaId).stream()
                .findFirst();
    }

    @Override
    public void agregarLinea(long ventaId, long productoId, String descripcion, LineaCalculada linea) {
        jdbc.update("""
                INSERT INTO venta_linea (venta_id, producto_id, descripcion, cantidad, precio_unitario, subtotal_con_iva,
                                         base_imponible, iva)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", ventaId, productoId, descripcion, linea.cantidad(),
                linea.precioUnitario(), linea.subtotalConIva(), linea.base(), linea.iva());
    }

    @Override
    public void actualizarLinea(long lineaId, LineaCalculada linea) {
        jdbc.update("""
                UPDATE venta_linea SET cantidad = ?, subtotal_con_iva = ?, base_imponible = ?, iva = ?
                WHERE id = ? AND eliminada_en IS NULL""", linea.cantidad(), linea.subtotalConIva(), linea.base(),
                linea.iva(), lineaId);
    }

    @Override
    public boolean eliminarLinea(long lineaId, long usuarioId) {
        return jdbc.update("""
                UPDATE venta_linea SET eliminada_en = now(), eliminada_por = ?
                WHERE id = ? AND eliminada_en IS NULL""", usuarioId, lineaId) == 1;
    }

    private Optional<Venta> unaVenta(String sql, Object... parametros) {
        return jdbc.query(sql, (rs, fila) -> {
            long id = rs.getLong("id");
            return new Venta(
                    id,
                    rs.getLong("numero"),
                    rs.getLong("turno_id"),
                    rs.getLong("caja_id"),
                    rs.getString("caja_nombre"),
                    rs.getLong("usuario_id"),
                    EstadoVenta.valueOf(rs.getString("estado")),
                    rs.getBigDecimal("tasa_iva"),
                    rs.getObject("creada_en", OffsetDateTime.class),
                    rs.getObject("suspendida_en", OffsetDateTime.class),
                    lineas(id));
        }, parametros).stream().findFirst();
    }

    private List<LineaVenta> lineas(long ventaId) {
        return jdbc.query(SELECT_LINEA + "AND l.venta_id = ? ORDER BY l.id", LINEA, ventaId);
    }
}
