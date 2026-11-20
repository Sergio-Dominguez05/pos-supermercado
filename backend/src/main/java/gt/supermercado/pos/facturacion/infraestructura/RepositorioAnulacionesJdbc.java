package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.facturacion.dominio.RepositorioAnulaciones;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class RepositorioAnulacionesJdbc implements RepositorioAnulaciones {

    private static final String SELECT_EMITIDA = """
            SELECT f.id AS factura_id, v.id AS venta_id, v.numero AS venta_numero, t.id AS turno_id,
                   t.estado = 'ABIERTO' AS turno_abierto, c.nombre AS caja, f.estado, f.serie, f.numero,
                   f.uuid_autorizacion, f.fecha_certificacion, f.emitida_en, v.total, f.receptor_id, f.receptor_nombre,
                   a.motivo, a.anulada_en
            FROM factura f
            JOIN venta v ON v.id = f.venta_id
            JOIN turno t ON t.id = v.turno_id
            JOIN caja c ON c.id = v.caja_id
            LEFT JOIN anulacion a ON a.factura_id = f.id
            """;

    private static final RowMapper<FacturaEmitida> EMITIDA = (rs, fila) -> new FacturaEmitida(
            rs.getLong("factura_id"),
            rs.getLong("venta_id"),
            rs.getLong("venta_numero"),
            rs.getLong("turno_id"),
            rs.getBoolean("turno_abierto"),
            rs.getString("caja"),
            EstadoFactura.valueOf(rs.getString("estado")),
            rs.getString("serie"),
            rs.getString("numero"),
            rs.getObject("uuid_autorizacion", UUID.class),
            rs.getObject("fecha_certificacion", OffsetDateTime.class),
            rs.getObject("emitida_en", OffsetDateTime.class),
            rs.getBigDecimal("total"),
            rs.getString("receptor_id"),
            rs.getString("receptor_nombre"),
            rs.getString("motivo"),
            rs.getObject("anulada_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    RepositorioAnulacionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FacturaEmitida> buscar(String texto, LocalDate fecha, int limite) {
        return buscar("('CERTIFICADA', 'ANULADA')", texto, fecha, null, limite);
    }

    @Override
    public List<FacturaEmitida> buscarCertificadas(String texto, LocalDate fecha, Long turnoId, int limite) {
        return buscar("('CERTIFICADA')", texto, fecha, turnoId, limite);
    }

    @Override
    public Optional<FacturaEmitida> ultimaCertificadaDeLaCaja(long cajaId) {
        return jdbc.query(SELECT_EMITIDA + """
                WHERE f.estado = 'CERTIFICADA' AND v.caja_id = ?
                ORDER BY f.emitida_en DESC, f.id DESC LIMIT 1""", EMITIDA, cajaId).stream().findFirst();
    }

    private List<FacturaEmitida> buscar(String estados, String texto, LocalDate fecha, Long turnoId, int limite) {
        StringBuilder sql = new StringBuilder(SELECT_EMITIDA).append("WHERE f.estado IN ").append(estados)
                .append('\n');
        List<Object> parametros = new ArrayList<>();
        if (turnoId != null) {
            sql.append("  AND v.turno_id = ?\n");
            parametros.add(turnoId);
        }
        if (texto != null && !texto.isBlank()) {
            sql.append("""
                      AND (f.numero ILIKE ? OR f.serie ILIKE ? OR f.uuid_autorizacion::text ILIKE ?
                           OR v.numero::text = ?)
                    """);
            String patron = "%" + texto.strip().replace("%", "").replace("_", "") + "%";
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(texto.strip());
        }
        if (fecha != null) {
            sql.append("  AND (f.emitida_en AT TIME ZONE 'America/Guatemala')::date = ?\n");
            parametros.add(fecha);
        }
        sql.append("ORDER BY f.emitida_en DESC, f.id DESC LIMIT ?");
        parametros.add(limite);
        return jdbc.query(sql.toString(), EMITIDA, parametros.toArray());
    }

    @Override
    public Optional<FacturaEmitida> porId(long facturaId) {
        return jdbc.query(SELECT_EMITIDA + "WHERE f.id = ?", EMITIDA, facturaId).stream().findFirst();
    }

    @Override
    @Transactional
    public boolean anular(long facturaId, String motivo, long usuarioId, long turnoId, String respuestaJson) {
        if (jdbc.update("UPDATE factura SET estado = 'ANULADA' WHERE id = ? AND estado = 'CERTIFICADA'", facturaId)
                == 0) {
            return false;
        }
        jdbc.update("""
                INSERT INTO anulacion (factura_id, motivo, usuario_id, turno_id, respuesta_fel)
                VALUES (?, ?, ?, ?, ?::jsonb)""", facturaId, motivo, usuarioId, turnoId, respuestaJson);
        jdbc.update("""
                INSERT INTO fel_intento (factura_id, operacion, exitoso, respuesta)
                VALUES (?, 'ANULAR', true, ?::jsonb)""", facturaId, respuestaJson);
        return true;
    }

    @Override
    public void intentoFallido(long facturaId, String codigo, String respuestaJson) {
        jdbc.update("""
                INSERT INTO fel_intento (factura_id, operacion, exitoso, codigo_error, respuesta)
                VALUES (?, 'ANULAR', false, ?, ?::jsonb)""", facturaId, codigo, respuestaJson);
    }
}
