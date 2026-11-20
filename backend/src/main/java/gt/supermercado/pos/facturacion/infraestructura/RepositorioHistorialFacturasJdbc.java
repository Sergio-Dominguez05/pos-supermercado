package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.DetalleFactura;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.FiltrosHistorial;
import gt.supermercado.pos.facturacion.dominio.HistorialFacturas;
import gt.supermercado.pos.facturacion.dominio.RepositorioHistorialFacturas;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class RepositorioHistorialFacturasJdbc implements RepositorioHistorialFacturas {

    private static final String DESDE_FACTURAS = """
            FROM factura f
            JOIN venta v ON v.id = f.venta_id
            JOIN caja c ON c.id = v.caja_id
            JOIN usuario u ON u.id = v.usuario_id
            """;

    private static final RowMapper<HistorialFacturas.Fila> FILA = (rs, i) -> new HistorialFacturas.Fila(
            rs.getLong("id"),
            rs.getLong("venta_numero"),
            rs.getObject("emitida_en", OffsetDateTime.class),
            rs.getString("serie"),
            rs.getString("numero"),
            rs.getObject("uuid_autorizacion", UUID.class),
            rs.getObject("numero_acceso_contingencia", Long.class),
            rs.getString("receptor_id"),
            rs.getString("receptor_nombre"),
            rs.getBigDecimal("total"),
            EstadoFactura.valueOf(rs.getString("estado")),
            rs.getString("caja"),
            rs.getString("cajero"));

    private final JdbcTemplate jdbc;

    RepositorioHistorialFacturasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HistorialFacturas buscar(FiltrosHistorial filtros) {
        StringBuilder condiciones = new StringBuilder("WHERE true\n");
        List<Object> parametros = new ArrayList<>();
        if (filtros.desde() != null) {
            condiciones.append("  AND f.emitida_en >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            parametros.add(filtros.desde());
        }
        if (filtros.hasta() != null) {
            condiciones.append("  AND f.emitida_en < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            parametros.add(filtros.hasta());
        }
        if (filtros.estado() != null) {
            condiciones.append("  AND f.estado = ?\n");
            parametros.add(filtros.estado().name());
        }
        if (filtros.cajaId() != null) {
            condiciones.append("  AND v.caja_id = ?\n");
            parametros.add(filtros.cajaId());
        }
        if (filtros.cajeroId() != null) {
            condiciones.append("  AND v.usuario_id = ?\n");
            parametros.add(filtros.cajeroId());
        }
        if (filtros.cliente() != null) {
            condiciones.append("""
                      AND (f.receptor_id ILIKE ? OR f_unaccent(lower(f.receptor_nombre)) LIKE f_unaccent(lower(?)))
                    """);
            String patron = patron(filtros.cliente());
            parametros.add(patron);
            parametros.add(patron);
        }
        if (filtros.buscar() != null) {
            condiciones.append("""
                      AND (f.numero ILIKE ? OR (f.serie || '-' || f.numero) ILIKE ? OR f.uuid_autorizacion::text ILIKE ?
                           OR f.numero_acceso_contingencia::text = ? OR v.numero::text = ?)
                    """);
            String patron = patron(filtros.buscar());
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(filtros.buscar());
            parametros.add(filtros.buscar());
        }
        long total = jdbc.queryForObject("SELECT count(*) " + DESDE_FACTURAS + condiciones, Long.class,
                parametros.toArray());
        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(FiltrosHistorial.TAMANO_PAGINA);
        conPagina.add((long) filtros.pagina() * FiltrosHistorial.TAMANO_PAGINA);
        List<HistorialFacturas.Fila> filas = jdbc.query("""
                SELECT f.id, v.numero AS venta_numero, f.emitida_en, f.serie, f.numero, f.uuid_autorizacion,
                       f.numero_acceso_contingencia, f.receptor_id, f.receptor_nombre, v.total, f.estado,
                       c.nombre AS caja, u.nombre_completo || ' (' || u.username || ')' AS cajero
                """ + DESDE_FACTURAS + condiciones + "ORDER BY f.emitida_en DESC, f.id DESC LIMIT ? OFFSET ?",
                FILA, conPagina.toArray());
        return new HistorialFacturas(filas, filtros.pagina(), FiltrosHistorial.TAMANO_PAGINA, total);
    }

    /** Busca el texto en cualquier parte, sin que % ni _ actúen como comodines. */
    private static String patron(String texto) {
        return "%" + texto.replace("\\", "").replace("%", "").replace("_", "") + "%";
    }

    @Override
    public Optional<DetalleFactura> detalle(long facturaId) {
        return jdbc.query("""
                SELECT f.id, v.id AS venta_id, v.numero AS venta_numero, f.estado, c.nombre AS caja,
                       u.nombre_completo || ' (' || u.username || ')' AS cajero,
                       (SELECT t.estado = 'ABIERTO' FROM turno t WHERE t.id = v.turno_id) AS turno_abierto, f.emitida_en,
                       f.emisor_snapshot->>'nombreComercial' AS nombre_comercial,
                       f.emisor_snapshot->>'razonSocial' AS razon_social, f.emisor_snapshot->>'nit' AS nit,
                       f.emisor_snapshot->>'direccionFiscal' AS direccion_fiscal,
                       f.emisor_snapshot->'establecimiento'->>'nombre' AS establecimiento,
                       f.emisor_snapshot->'establecimiento'->>'codigoSat' AS codigo_establecimiento,
                       f.emisor_snapshot->'establecimiento'->>'direccion' AS direccion_establecimiento,
                       f.receptor_tipo, f.receptor_id, f.receptor_nombre, f.receptor_direccion, f.receptor_correo,
                       f.serie, f.numero, f.uuid_autorizacion, f.fecha_certificacion, f.numero_acceso_contingencia,
                       v.subtotal, v.iva, v.total
                """ + DESDE_FACTURAS + "WHERE f.id = ?", (rs, i) -> new DetalleFactura(
                rs.getLong("id"),
                rs.getLong("venta_id"),
                rs.getLong("venta_numero"),
                EstadoFactura.valueOf(rs.getString("estado")),
                rs.getString("caja"),
                rs.getString("cajero"),
                rs.getBoolean("turno_abierto"),
                rs.getObject("emitida_en", OffsetDateTime.class),
                new DetalleFactura.Emisor(rs.getString("nombre_comercial"), rs.getString("razon_social"),
                        rs.getString("nit"), rs.getString("direccion_fiscal"), rs.getString("establecimiento"),
                        rs.getString("codigo_establecimiento"), rs.getString("direccion_establecimiento")),
                new DetalleFactura.Receptor(rs.getString("receptor_tipo"), rs.getString("receptor_id"),
                        rs.getString("receptor_nombre"), rs.getString("receptor_direccion"),
                        rs.getString("receptor_correo")),
                new DetalleFactura.Certificacion(rs.getString("serie"), rs.getString("numero"),
                        rs.getObject("uuid_autorizacion", UUID.class),
                        rs.getObject("fecha_certificacion", OffsetDateTime.class),
                        rs.getObject("numero_acceso_contingencia", Long.class)),
                List.of(),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("iva"),
                rs.getBigDecimal("total"),
                List.of(),
                List.of()), facturaId).stream().findFirst();
    }

    @Override
    public List<DetalleFactura.Linea> lineas(long ventaId) {
        return jdbc.query("""
                SELECT descripcion, cantidad, precio_unitario, subtotal_con_iva FROM venta_linea
                WHERE venta_id = ? AND eliminada_en IS NULL ORDER BY id""",
                (rs, i) -> new DetalleFactura.Linea(rs.getString("descripcion"), rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"),
                        rs.getBigDecimal("subtotal_con_iva").setScale(2, RoundingMode.HALF_UP)), ventaId);
    }

    @Override
    public List<DetalleFactura.Pago> pagos(long ventaId) {
        return jdbc.query("""
                SELECT tipo_tarjeta, autorizacion, ultimos4, monto FROM pago
                WHERE venta_id = ? AND eliminado_en IS NULL ORDER BY id""",
                (rs, i) -> new DetalleFactura.Pago(rs.getString("tipo_tarjeta"), rs.getString("autorizacion"),
                        rs.getString("ultimos4"), rs.getBigDecimal("monto")), ventaId);
    }

    @Override
    public List<EventoRegistrado> eventos(long facturaId) {
        return jdbc.query("""
                SELECT momento, origen, operacion, exitoso, codigo_error, mensaje, usuario, caja FROM (
                    SELECT i.intentado_en AS momento, 'FEL' AS origen, i.operacion, i.exitoso, i.codigo_error,
                           CASE WHEN i.operacion = 'CORREO'
                                THEN concat_ws(': ', i.respuesta->>'destinatario', i.respuesta->>'mensaje')
                                ELSE coalesce(i.respuesta->>'mensaje', i.respuesta->>'error') END AS mensaje,
                           NULL AS usuario, NULL AS caja, i.id AS orden
                    FROM fel_intento i WHERE i.factura_id = ?
                    UNION ALL
                    SELECT r.impresa_en, 'REIMPRESION', NULL, true, NULL, NULL,
                           u.nombre_completo || ' (' || u.username || ')', c.nombre, r.id
                    FROM reimpresion r
                    JOIN usuario u ON u.id = r.usuario_id
                    LEFT JOIN caja c ON c.id = r.caja_id
                    WHERE r.factura_id = ?
                    UNION ALL
                    SELECT a.anulada_en, 'ANULACION', NULL, true, NULL, a.motivo,
                           u.nombre_completo || ' (' || u.username || ')', NULL, 0
                    FROM anulacion a
                    JOIN usuario u ON u.id = a.usuario_id
                    WHERE a.factura_id = ?
                ) eventos
                ORDER BY momento, orden""",
                (rs, i) -> new EventoRegistrado(rs.getObject("momento", OffsetDateTime.class),
                        rs.getString("origen"), rs.getString("operacion"), (Boolean) rs.getObject("exitoso"),
                        rs.getString("codigo_error"), rs.getString("mensaje"), rs.getString("usuario"),
                        rs.getString("caja")),
                facturaId, facturaId, facturaId);
    }

    @Override
    public List<Opcion> cajas() {
        return jdbc.query("SELECT id, nombre, activo FROM caja ORDER BY lower(nombre)",
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre")
                        + (rs.getBoolean("activo") ? "" : " (inactiva)")));
    }

    @Override
    public List<Opcion> cajeros() {
        return jdbc.query("""
                SELECT DISTINCT u.id, u.nombre_completo, u.username
                FROM factura f JOIN venta v ON v.id = f.venta_id JOIN usuario u ON u.id = v.usuario_id
                ORDER BY u.nombre_completo, u.username""",
                (rs, i) -> new Opcion(rs.getLong("id"),
                        rs.getString("nombre_completo") + " (" + rs.getString("username") + ")"));
    }
}
