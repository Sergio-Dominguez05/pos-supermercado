package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.CambioRegistrado;
import gt.supermercado.pos.bitacora.dominio.DetalleEvento;
import gt.supermercado.pos.bitacora.dominio.DetalleEvento.Documento;
import gt.supermercado.pos.bitacora.dominio.FiltrosBitacora;
import gt.supermercado.pos.bitacora.dominio.LecturaBitacora;
import gt.supermercado.pos.bitacora.dominio.OpcionBitacora;
import gt.supermercado.pos.bitacora.dominio.PaginaBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroConsultado;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class LecturaBitacoraJdbc implements LecturaBitacora {

    private static final String SELECT_REGISTRO = """
            SELECT b.id, coalesce(b.secuencia, 0) AS secuencia, b.ocurrido_en,
                   u.nombre_completo || ' (' || u.username || ')' AS usuario, b.rol, c.nombre AS caja, b.modulo,
                   b.accion, b.exitoso, b.descripcion, b.entidad, b.entidad_id, b.cambios::text AS cambios
            FROM bitacora b
            LEFT JOIN usuario u ON u.id = b.usuario_id
            LEFT JOIN caja c ON c.id = b.caja_id
            WHERE true
            """;

    private static final RowMapper<RegistroConsultado> REGISTRO = (rs, fila) -> new RegistroConsultado(
            rs.getLong("id"), rs.getLong("secuencia"), rs.getObject("ocurrido_en", OffsetDateTime.class),
            rs.getString("usuario"), rs.getString("rol"), rs.getString("caja"), rs.getString("modulo"),
            rs.getString("accion"), rs.getBoolean("exitoso"), rs.getString("descripcion"));

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    LecturaBitacoraJdbc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<CambioRegistrado> cambios(AccionSensible accion, String entidad, long entidadId, String campo) {
        return jdbc.query("""
                SELECT b.ocurrido_en, b.cambios -> ? ->> 'antes' AS antes, b.cambios -> ? ->> 'despues' AS despues,
                       u.nombre_completo || ' (' || u.username || ')' AS usuario
                FROM bitacora b LEFT JOIN usuario u ON u.id = b.usuario_id
                WHERE b.accion = ? AND b.exitoso AND b.entidad = ? AND b.entidad_id = ? AND b.cambios -> ? IS NOT NULL
                ORDER BY b.ocurrido_en DESC, b.secuencia DESC""",
                (rs, fila) -> new CambioRegistrado(rs.getObject("ocurrido_en", OffsetDateTime.class),
                        rs.getString("antes"), rs.getString("despues"), rs.getString("usuario")),
                campo, campo, accion.name(), entidad, entidadId, campo);
    }

    @Override
    public PaginaBitacora buscar(FiltrosBitacora filtros) {
        StringBuilder condiciones = new StringBuilder();
        List<Object> argumentos = new ArrayList<>();
        if (filtros.desde() != null) {
            condiciones.append("  AND b.ocurrido_en >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            argumentos.add(filtros.desde());
        }
        if (filtros.hasta() != null) {
            condiciones.append("  AND b.ocurrido_en < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            argumentos.add(filtros.hasta());
        }
        if (filtros.usuarioId() != null) {
            condiciones.append("  AND b.usuario_id = ?\n");
            argumentos.add(filtros.usuarioId());
        }
        if (filtros.cajaId() != null) {
            condiciones.append("  AND b.caja_id = ?\n");
            argumentos.add(filtros.cajaId());
        }
        if (filtros.modulo() != null) {
            condiciones.append("  AND b.modulo = ?\n");
            argumentos.add(filtros.modulo());
        }
        if (filtros.accion() != null) {
            condiciones.append("  AND b.accion = ?\n");
            argumentos.add(filtros.accion());
        }
        if (filtros.exitoso() != null) {
            condiciones.append("  AND b.exitoso = ?\n");
            argumentos.add(filtros.exitoso());
        }
        if (filtros.documento() != null) {
            documento(filtros.documento(), condiciones, argumentos);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM bitacora b WHERE true\n" + condiciones, Long.class,
                argumentos.toArray());
        List<Object> pagina = new ArrayList<>(argumentos);
        pagina.add(FiltrosBitacora.TAMANO_PAGINA);
        pagina.add((long) filtros.pagina() * FiltrosBitacora.TAMANO_PAGINA);
        List<RegistroConsultado> registros = jdbc.query(SELECT_REGISTRO + condiciones
                + "ORDER BY b.ocurrido_en DESC, b.secuencia DESC NULLS LAST, b.id DESC LIMIT ? OFFSET ?", REGISTRO,
                pagina.toArray());
        return new PaginaBitacora(registros, filtros.pagina(), FiltrosBitacora.TAMANO_PAGINA, total);
    }

    /**
     * HU-11.07 crit. 2: un número es el de una venta (también sus pagos y su factura), el de una factura o el de un
     * cierre (el turno); "serie-número" es una factura.
     */
    private static void documento(String documento, StringBuilder condiciones, List<Object> argumentos) {
        if (documento.matches("\\d{1,18}")) {
            long numero = Long.parseLong(documento);
            condiciones.append("""
                      AND ((b.entidad = 'venta' AND b.entidad_id IN (SELECT id FROM venta WHERE numero = ?))
                        OR (b.entidad = 'pago' AND b.entidad_id IN
                            (SELECT p.id FROM pago p JOIN venta v ON v.id = p.venta_id WHERE v.numero = ?))
                        OR (b.entidad = 'factura' AND b.entidad_id IN
                            (SELECT f.id FROM factura f JOIN venta v ON v.id = f.venta_id
                             WHERE f.numero = ? OR v.numero = ?))
                        OR (b.entidad = 'turno' AND b.entidad_id = ?))
                    """);
            argumentos.addAll(List.of(numero, numero, documento, numero, numero));
        } else {
            condiciones.append("""
                      AND b.entidad = 'factura' AND b.entidad_id IN
                          (SELECT id FROM factura WHERE upper(serie || '-' || numero) = upper(?))
                    """);
            argumentos.add(documento.replace(" ", ""));
        }
    }

    @Override
    public Optional<DetalleEvento> detalle(long id) {
        return jdbc.query(SELECT_REGISTRO + "  AND b.id = ?", (rs, fila) -> {
            RegistroConsultado registro = REGISTRO.mapRow(rs, fila);
            String entidad = rs.getString("entidad");
            Long entidadId = (Long) rs.getObject("entidad_id");
            String cambios = rs.getString("cambios");
            return new DetalleEvento(registro, entidad, entidadId, cambios == null ? null : leer(cambios),
                    entidad == null || entidadId == null ? null : documentoDe(entidad, entidadId));
        }, id).stream().findFirst();
    }

    /** HU-11.08 crit. 2: lo que se puede abrir de la entidad del evento; la venta y su pago llevan a su factura. */
    private Documento documentoDe(String entidad, long id) {
        return switch (entidad) {
            case "factura" -> factura("f.id = ?", id);
            case "venta" -> factura("f.venta_id = ?", id);
            case "pago" -> factura("f.venta_id = (SELECT venta_id FROM pago WHERE id = ?)", id);
            case "turno" -> jdbc.query("SELECT id FROM turno WHERE id = ? AND estado = 'CERRADO'",
                    (rs, fila) -> new Documento("CIERRE", rs.getLong("id"), "Cierre del turno " + rs.getLong("id")), id)
                    .stream().findFirst().orElse(null);
            case "producto" -> jdbc.query("SELECT id, descripcion FROM producto WHERE id = ?",
                    (rs, fila) -> new Documento("PRODUCTO", rs.getLong("id"), rs.getString("descripcion")), id)
                    .stream().findFirst().orElse(null);
            case "usuario" -> jdbc.query("SELECT id, nombre_completo, username FROM usuario WHERE id = ?",
                    (rs, fila) -> new Documento("USUARIO", rs.getLong("id"),
                            rs.getString("nombre_completo") + " (" + rs.getString("username") + ")"), id)
                    .stream().findFirst().orElse(null);
            default -> null;
        };
    }

    private Documento factura(String condicion, long id) {
        return jdbc.query("""
                SELECT f.id, f.serie, f.numero, v.numero AS venta FROM factura f JOIN venta v ON v.id = f.venta_id
                WHERE\s""" + condicion, (rs, fila) -> new Documento("FACTURA", rs.getLong("id"),
                rs.getString("numero") == null ? "Factura de la venta N.º " + rs.getLong("venta")
                        : "Factura " + rs.getString("serie") + "-" + rs.getString("numero")), id)
                .stream().findFirst().orElse(null);
    }

    @Override
    public List<OpcionBitacora> usuarios() {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.username FROM usuario u
                WHERE EXISTS (SELECT 1 FROM bitacora b WHERE b.usuario_id = u.id)
                ORDER BY u.nombre_completo, u.username""", (rs, fila) -> new OpcionBitacora(rs.getLong("id"),
                rs.getString("nombre_completo") + " (" + rs.getString("username") + ")"));
    }

    @Override
    public List<OpcionBitacora> cajas() {
        return jdbc.query("""
                SELECT c.id, c.nombre FROM caja c WHERE EXISTS (SELECT 1 FROM bitacora b WHERE b.caja_id = c.id)
                ORDER BY lower(c.nombre)""", (rs, fila) -> new OpcionBitacora(rs.getLong("id"), rs.getString("nombre")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leer(String cambios) {
        return json.readValue(cambios, Map.class);
    }
}
