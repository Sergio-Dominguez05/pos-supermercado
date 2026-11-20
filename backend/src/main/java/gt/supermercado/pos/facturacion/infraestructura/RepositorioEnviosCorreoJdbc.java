package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.EnvioPendiente;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.RepositorioEnviosCorreo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** HU-08.15: la tarea {@code ENVIAR_CORREO} de cada factura (Architecture.md §6.4) y sus intentos. */
@Repository
class RepositorioEnviosCorreoJdbc implements RepositorioEnviosCorreo {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    RepositorioEnviosCorreoJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Long> porEnviar(int limite) {
        return jdbc.queryForList("""
                SELECT t.factura_id FROM tarea_pendiente t JOIN factura f ON f.id = t.factura_id
                WHERE t.tipo = 'ENVIAR_CORREO' AND NOT t.completada AND t.proximo_intento <= now()
                  AND f.estado IN ('CERTIFICADA', 'ANULADA')
                ORDER BY t.proximo_intento, t.id
                LIMIT ?""", Long.class, limite);
    }

    @Override
    public Optional<EnvioPendiente> bloquear(long facturaId, boolean aunqueEspere) {
        return jdbc.query("""
                SELECT f.id, f.venta_id, v.numero AS venta_numero, f.estado, f.receptor_correo, f.serie, f.numero,
                       f.uuid_autorizacion, coalesce(f.xml_certificado, f.xml_enviado) AS xml, t.intentos
                FROM tarea_pendiente t
                JOIN factura f ON f.id = t.factura_id
                JOIN venta v ON v.id = f.venta_id
                WHERE t.factura_id = ? AND t.tipo = 'ENVIAR_CORREO' AND NOT t.completada
                  AND (? OR t.proximo_intento <= now())
                FOR UPDATE OF t SKIP LOCKED""", (rs, fila) -> new EnvioPendiente(rs.getLong("id"),
                rs.getLong("venta_id"), rs.getLong("venta_numero"), EstadoFactura.valueOf(rs.getString("estado")),
                rs.getString("receptor_correo"), rs.getString("serie"), rs.getString("numero"),
                rs.getObject("uuid_autorizacion", UUID.class), rs.getString("xml"), rs.getInt("intentos")),
                facturaId, aunqueEspere).stream().findFirst();
    }

    @Override
    public void programar(long facturaId) {
        jdbc.update("""
                INSERT INTO tarea_pendiente (tipo, factura_id, proximo_intento) VALUES ('ENVIAR_CORREO', ?, now())
                ON CONFLICT (factura_id) WHERE tipo = 'ENVIAR_CORREO' AND NOT completada DO NOTHING""", facturaId);
    }

    @Override
    public void completar(long facturaId, String error) {
        jdbc.update("""
                UPDATE tarea_pendiente SET completada = true, ultimo_error = ?
                WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO' AND NOT completada""", error, facturaId);
    }

    @Override
    public void reprogramar(long facturaId, String error, boolean contarIntento) {
        jdbc.update(contarIntento ? """
                UPDATE tarea_pendiente SET intentos = intentos + 1, ultimo_error = ?,
                       proximo_intento = now() + interval '1 minute' * power(2, least(intentos, 10))
                WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO' AND NOT completada""" : """
                UPDATE tarea_pendiente SET ultimo_error = ?, proximo_intento = now() + interval '5 minutes'
                WHERE factura_id = ? AND tipo = 'ENVIAR_CORREO' AND NOT completada""", error, facturaId);
    }

    @Override
    public void registrarIntento(long facturaId, boolean exitoso, String codigoError, Map<String, Object> respuesta) {
        jdbc.update("""
                INSERT INTO fel_intento (factura_id, operacion, exitoso, codigo_error, respuesta)
                VALUES (?, 'CORREO', ?, ?, ?::jsonb)""", facturaId, exitoso, codigoError,
                json.writeValueAsString(respuesta));
    }
}
