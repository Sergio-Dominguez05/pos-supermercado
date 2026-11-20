package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.Factura;
import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.facturacion.dominio.FacturaPendiente;
import gt.supermercado.pos.facturacion.dominio.FacturaRechazada;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class RepositorioFacturasJdbc implements RepositorioFacturas {

    /** Los estados en los que una factura todavía se puede certificar (HU-08.04 crit. 3). */
    private static final String SIN_CERTIFICAR = "('PENDIENTE', 'CONTINGENCIA')";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    RepositorioFacturasJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public long registrar(DatosDte datos, String xmlEnviado) {
        Long id = jdbc.queryForObject("""
                INSERT INTO factura (venta_id, receptor_tipo, receptor_id, receptor_nombre, emisor_snapshot, emitida_en,
                                     xml_enviado)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING id""", Long.class, datos.venta().id(), datos.receptor().tipo().name(),
                datos.receptor().identificacion(), datos.receptor().nombre(),
                json.writeValueAsString(copiaDelEmisor(datos.emisor(), datos.establecimiento())), datos.emitidaEn(),
                xmlEnviado);
        jdbc.update("INSERT INTO tarea_pendiente (tipo, factura_id) VALUES ('CERTIFICAR', ?)", id);
        return id;
    }

    @Override
    public Optional<Factura> deLaVenta(long ventaId) {
        return jdbc.query("""
                SELECT f.id, f.venta_id, v.uuid, f.estado, f.uuid_autorizacion, f.serie, f.numero, f.fecha_certificacion,
                       f.emitida_en, f.xml_enviado, f.numero_acceso_contingencia
                FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE f.venta_id = ?""", (rs, fila) -> new Factura(
                rs.getLong("id"),
                rs.getLong("venta_id"),
                rs.getObject("uuid", UUID.class),
                EstadoFactura.valueOf(rs.getString("estado")),
                rs.getObject("uuid_autorizacion", UUID.class),
                rs.getString("serie"),
                rs.getString("numero"),
                rs.getObject("fecha_certificacion", OffsetDateTime.class),
                rs.getObject("emitida_en", OffsetDateTime.class),
                rs.getString("xml_enviado"),
                rs.getObject("numero_acceso_contingencia", Long.class)), ventaId).stream().findFirst();
    }

    @Override
    public Optional<FacturaImprimible> imprimible(long ventaId) {
        return deLaVenta(ventaId).flatMap(factura -> jdbc.query("""
                SELECT receptor_tipo, receptor_id, receptor_nombre, emisor_snapshot::text AS emisor
                FROM factura WHERE id = ?""", (rs, fila) -> {
            Map<String, Object> emisor = leer(rs.getString("emisor"));
            @SuppressWarnings("unchecked")
            Map<String, Object> establecimiento = (Map<String, Object>) emisor.get("establecimiento");
            return new FacturaImprimible(factura,
                    new Receptor(Receptor.TipoReceptor.valueOf(rs.getString("receptor_tipo")),
                            rs.getString("receptor_id"), rs.getString("receptor_nombre")),
                    new Emisor(texto(emisor, "nit"), texto(emisor, "razonSocial"), texto(emisor, "nombreComercial"),
                            texto(emisor, "direccionFiscal"), texto(emisor, "telefono"), texto(emisor, "correo"),
                            AfiliacionIva.valueOf(texto(emisor, "afiliacionIva")), null),
                    new Establecimiento(0, texto(establecimiento, "codigoSat"), texto(establecimiento, "nombre"),
                            texto(establecimiento, "direccion"), texto(establecimiento, "codigoPostal"),
                            texto(establecimiento, "municipio"), texto(establecimiento, "departamento"), null));
        }, factura.id()).stream().findFirst());
    }

    /** El último intento fallido de certificar cada factura rechazada, con su venta, caja y cajero. */
    private static final String SELECT_RECHAZADA = """
            SELECT f.id AS factura_id, v.id AS venta_id, v.numero, c.nombre AS caja, u.nombre_completo AS cajero,
                   f.emitida_en, v.total, f.receptor_id, f.receptor_nombre, i.codigo_error,
                   i.respuesta->>'mensaje' AS mensaje, i.respuesta->>'respuesta' AS respuesta, i.intentado_en
            FROM factura f
            JOIN venta v ON v.id = f.venta_id
            JOIN caja c ON c.id = v.caja_id
            JOIN usuario u ON u.id = v.usuario_id
            JOIN LATERAL (SELECT * FROM fel_intento fi
                          WHERE fi.factura_id = f.id AND fi.operacion = 'CERTIFICAR' AND NOT fi.exitoso
                          ORDER BY fi.id DESC LIMIT 1) i ON true
            """;

    private static final RowMapper<FacturaRechazada> RECHAZADA = (rs, fila) -> new FacturaRechazada(
            rs.getLong("factura_id"),
            rs.getLong("venta_id"),
            rs.getLong("numero"),
            rs.getString("caja"),
            rs.getString("cajero"),
            rs.getObject("emitida_en", OffsetDateTime.class),
            rs.getBigDecimal("total"),
            rs.getString("receptor_id"),
            rs.getString("receptor_nombre"),
            rs.getString("codigo_error"),
            rs.getString("mensaje"),
            rs.getString("respuesta"),
            rs.getObject("intentado_en", OffsetDateTime.class));

    @Override
    public Optional<FacturaRechazada> rechazoDe(long facturaId) {
        return jdbc.query(SELECT_RECHAZADA + "WHERE f.id = ? AND f.estado = 'RECHAZADA'", RECHAZADA, facturaId).stream()
                .findFirst();
    }

    @Override
    public List<FacturaRechazada> rechazadas() {
        return jdbc.query(SELECT_RECHAZADA + "WHERE f.estado = 'RECHAZADA' ORDER BY f.emitida_en, f.id", RECHAZADA);
    }

    @Override
    @Transactional
    public boolean reabrir(long facturaId, DatosDte datos, String xmlEnviado) {
        if (jdbc.update("""
                UPDATE factura SET estado = 'PENDIENTE', receptor_tipo = ?, receptor_id = ?, receptor_nombre = ?,
                                   emisor_snapshot = ?::jsonb, xml_enviado = ?
                WHERE id = ? AND estado = 'RECHAZADA'""", datos.receptor().tipo().name(),
                datos.receptor().identificacion(), datos.receptor().nombre(),
                json.writeValueAsString(copiaDelEmisor(datos.emisor(), datos.establecimiento())), xmlEnviado,
                facturaId) == 0) {
            return false;
        }
        jdbc.update("INSERT INTO tarea_pendiente (tipo, factura_id) VALUES ('CERTIFICAR', ?)", facturaId);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leer(String copia) {
        return json.readValue(copia, Map.class);
    }

    private static String texto(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        return valor == null ? null : valor.toString();
    }

    @Override
    @Transactional
    public boolean certificada(long facturaId, ResultadoCertificacion resultado) {
        if (jdbc.update("""
                UPDATE factura SET estado = 'CERTIFICADA', uuid_autorizacion = ?, serie = ?, numero = ?,
                                   fecha_certificacion = ?
                WHERE id = ? AND estado IN """ + SIN_CERTIFICAR, resultado.autorizacion(), resultado.serie(),
                resultado.numero(), resultado.fechaCertificacion(), facturaId) == 0) {
            return false;
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("autorizacion", resultado.autorizacion().toString());
        respuesta.put("serie", resultado.serie());
        respuesta.put("numero", resultado.numero());
        respuesta.put("fechaCertificacion", resultado.fechaCertificacion().toString());
        intento(facturaId, true, null, respuesta);
        completarTarea(facturaId, null);
        return true;
    }

    @Override
    @Transactional
    public boolean rechazada(long facturaId, ResultadoCertificacion resultado) {
        if (jdbc.update("UPDATE factura SET estado = 'RECHAZADA' WHERE id = ? AND estado IN " + SIN_CERTIFICAR,
                facturaId) == 0) {
            return false;
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("codigo", resultado.codigoError());
        respuesta.put("mensaje", resultado.mensajeError());
        respuesta.put("respuesta", resultado.respuesta());   // HU-08.06 crit. 4: la respuesta completa
        intento(facturaId, false, resultado.codigoError(), respuesta);
        // Reenviarla después de corregir los datos es de HU-08.06: no se reintenta sola.
        completarTarea(facturaId, resultado.mensajeError());
        return true;
    }

    @Override
    @Transactional
    public boolean enContingencia(long facturaId, String motivo, Long numeroAcceso, String xmlEnviado) {
        if (jdbc.update("""
                UPDATE factura SET estado = 'CONTINGENCIA',
                                   numero_acceso_contingencia = coalesce(numero_acceso_contingencia, ?::bigint),
                                   xml_enviado = CASE WHEN numero_acceso_contingencia IS NULL AND ?::text IS NOT NULL
                                                      THEN ?::text ELSE xml_enviado END
                WHERE id = ? AND estado IN """ + SIN_CERTIFICAR, numeroAcceso, xmlEnviado, xmlEnviado, facturaId) == 0) {
            return false;
        }
        intento(facturaId, false, "SIN_RESPUESTA", Map.of("error", motivo));
        // Espera exponencial (Architecture.md §6.4): 1, 2, 4, 8... minutos, hasta 30.
        jdbc.update("""
                UPDATE tarea_pendiente SET intentos = intentos + 1, ultimo_error = ?,
                       proximo_intento = now() + least(interval '30 minutes',
                                                       interval '1 minute' * power(2, least(intentos, 10)))
                WHERE factura_id = ? AND tipo = 'CERTIFICAR' AND NOT completada""", motivo, facturaId);
        return true;
    }

    @Override
    public long siguienteNumeroAcceso() {
        return jdbc.queryForObject("SELECT nextval('numero_acceso_contingencia_seq')", Long.class);
    }

    private static final String SELECT_PENDIENTE = """
            SELECT f.id AS factura_id, v.id AS venta_id, v.numero AS venta_numero, c.nombre AS caja, f.estado,
                   f.numero_acceso_contingencia, f.emitida_en, v.total, t.intentos, t.ultimo_error, t.proximo_intento
            FROM factura f
            JOIN venta v ON v.id = f.venta_id
            JOIN caja c ON c.id = v.caja_id
            JOIN tarea_pendiente t ON t.factura_id = f.id AND t.tipo = 'CERTIFICAR' AND NOT t.completada
            WHERE f.estado IN ('PENDIENTE', 'CONTINGENCIA')
            """;

    private static final RowMapper<FacturaPendiente> PENDIENTE = (rs, fila) -> new FacturaPendiente(
            rs.getLong("factura_id"),
            rs.getLong("venta_id"),
            rs.getLong("venta_numero"),
            rs.getString("caja"),
            EstadoFactura.valueOf(rs.getString("estado")),
            rs.getObject("numero_acceso_contingencia", Long.class),
            rs.getObject("emitida_en", OffsetDateTime.class),
            rs.getBigDecimal("total"),
            rs.getInt("intentos"),
            rs.getString("ultimo_error"),
            rs.getObject("proximo_intento", OffsetDateTime.class));

    @Override
    public List<FacturaPendiente> porCertificar(boolean todas, int limite) {
        return jdbc.query(SELECT_PENDIENTE + (todas ? "" : "  AND t.proximo_intento <= now()\n")
                + "ORDER BY f.emitida_en, f.id LIMIT ?", PENDIENTE, limite);
    }

    @Override
    public List<FacturaPendiente> pendientes() {
        return jdbc.query(SELECT_PENDIENTE + "ORDER BY f.emitida_en, f.id", PENDIENTE);
    }

    private void intento(long facturaId, boolean exitoso, String codigoError, Map<String, Object> respuesta) {
        jdbc.update("""
                INSERT INTO fel_intento (factura_id, operacion, exitoso, codigo_error, respuesta)
                VALUES (?, 'CERTIFICAR', ?, ?, ?::jsonb)""", facturaId, exitoso, codigoError,
                json.writeValueAsString(respuesta));
    }

    private void completarTarea(long facturaId, String error) {
        jdbc.update("""
                UPDATE tarea_pendiente SET completada = true, intentos = intentos + 1, ultimo_error = ?
                WHERE factura_id = ? AND tipo = 'CERTIFICAR' AND NOT completada""", error, facturaId);
    }

    /** La copia del emisor y del establecimiento al momento de emitir: cambiarlos después no altera la factura. */
    private static Map<String, Object> copiaDelEmisor(Emisor emisor, Establecimiento establecimiento) {
        Map<String, Object> copia = new LinkedHashMap<>();
        copia.put("nit", emisor.nit());
        copia.put("razonSocial", emisor.razonSocial());
        copia.put("nombreComercial", emisor.nombreComercial());
        copia.put("direccionFiscal", emisor.direccionFiscal());
        copia.put("telefono", emisor.telefono());
        copia.put("correo", emisor.correo());
        copia.put("afiliacionIva", emisor.afiliacionIva().name());
        Map<String, Object> deEstablecimiento = new LinkedHashMap<>();
        deEstablecimiento.put("codigoSat", establecimiento.codigoSat());
        deEstablecimiento.put("nombre", establecimiento.nombre());
        deEstablecimiento.put("direccion", establecimiento.direccion());
        deEstablecimiento.put("codigoPostal", establecimiento.codigoPostal());
        deEstablecimiento.put("municipio", establecimiento.municipio());
        deEstablecimiento.put("departamento", establecimiento.departamento());
        copia.put("establecimiento", deEstablecimiento);
        return copia;
    }
}
