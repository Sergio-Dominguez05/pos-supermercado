package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.Factura;
import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
                       f.emitida_en, f.xml_enviado
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
                rs.getString("xml_enviado")), ventaId).stream().findFirst();
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
        intento(facturaId, false, resultado.codigoError(), respuesta);
        // Reenviarla después de corregir los datos es de HU-08.06: no se reintenta sola.
        completarTarea(facturaId, resultado.mensajeError());
        return true;
    }

    @Override
    @Transactional
    public boolean enContingencia(long facturaId, String motivo) {
        if (jdbc.update("UPDATE factura SET estado = 'CONTINGENCIA' WHERE id = ? AND estado IN " + SIN_CERTIFICAR,
                facturaId) == 0) {
            return false;
        }
        intento(facturaId, false, "SIN_RESPUESTA", Map.of("error", motivo));
        jdbc.update("""
                UPDATE tarea_pendiente SET intentos = intentos + 1, ultimo_error = ?,
                                           proximo_intento = now() + interval '1 minute'
                WHERE factura_id = ? AND tipo = 'CERTIFICAR' AND NOT completada""", motivo, facturaId);
        return true;
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
