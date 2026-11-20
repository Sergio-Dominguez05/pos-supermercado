package gt.supermercado.pos.caja.infraestructura;

import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.FiltrosCierres;
import gt.supermercado.pos.caja.dominio.OpcionFiltro;
import gt.supermercado.pos.caja.dominio.PaginaCierres;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class RepositorioTurnosJdbc implements RepositorioTurnos {

    private static final String SELECT_TURNO = """
            SELECT t.id, t.caja_id, c.nombre AS caja_nombre, t.usuario_id, u.nombre_completo AS usuario_nombre,
                   t.estado, t.abierto_en
            FROM turno t
            JOIN caja c ON c.id = t.caja_id
            JOIN usuario u ON u.id = t.usuario_id
            """;

    private static final RowMapper<Turno> TURNO = (rs, fila) -> new Turno(
            rs.getLong("id"),
            rs.getLong("caja_id"),
            rs.getString("caja_nombre"),
            rs.getLong("usuario_id"),
            rs.getString("usuario_nombre"),
            EstadoTurno.valueOf(rs.getString("estado")),
            rs.getObject("abierto_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    RepositorioTurnosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Turno> buscarPorId(long id) {
        return jdbc.query(SELECT_TURNO + "WHERE t.id = ?", TURNO, id).stream().findFirst();
    }

    @Override
    public Optional<Turno> abiertoEnCaja(long cajaId) {
        return jdbc.query(SELECT_TURNO + "WHERE t.caja_id = ? AND t.estado = 'ABIERTO'", TURNO, cajaId)
                .stream().findFirst();
    }

    @Override
    public Optional<Turno> abiertoDeUsuario(long usuarioId) {
        return jdbc.query(SELECT_TURNO + "WHERE t.usuario_id = ? AND t.estado = 'ABIERTO'", TURNO, usuarioId)
                .stream().findFirst();
    }

    @Override
    public List<Turno> abiertos() {
        return jdbc.query(SELECT_TURNO
                + "WHERE t.estado = 'ABIERTO' ORDER BY c.nombre, t.abierto_en, t.id", TURNO);
    }

    @Override
    public long abrir(long cajaId, long usuarioId) {
        return jdbc.queryForObject("INSERT INTO turno (caja_id, usuario_id) VALUES (?, ?) RETURNING id",
                Long.class, cajaId, usuarioId);
    }

    private static final String SELECT_CIERRE = """
            SELECT t.id, t.caja_id, c.nombre AS caja_nombre, t.usuario_id, u.nombre_completo AS usuario_nombre,
                   t.abierto_en, t.cerrado_en, p.nombre_completo AS cerrado_por, t.total_registrado, t.total_lote,
                   t.diferencia, t.justificacion
            FROM turno t
            JOIN caja c ON c.id = t.caja_id
            JOIN usuario u ON u.id = t.usuario_id
            LEFT JOIN usuario p ON p.id = t.cerrado_por
            WHERE t.estado = 'CERRADO'
            """;

    private static final RowMapper<CierreDeTurno> CIERRE = (rs, fila) -> new CierreDeTurno(
            rs.getLong("id"),
            rs.getLong("caja_id"),
            rs.getString("caja_nombre"),
            rs.getLong("usuario_id"),
            rs.getString("usuario_nombre"),
            rs.getObject("abierto_en", OffsetDateTime.class),
            rs.getObject("cerrado_en", OffsetDateTime.class),
            rs.getString("cerrado_por"),
            rs.getBigDecimal("total_registrado"),
            rs.getBigDecimal("total_lote"),
            rs.getBigDecimal("diferencia"),
            rs.getString("justificacion"));

    @Override
    public boolean cerrar(long turnoId, long cerradoPor, BigDecimal totalRegistrado, BigDecimal totalLote,
                          BigDecimal diferencia, String justificacion) {
        return jdbc.update("""
                UPDATE turno SET estado = 'CERRADO', cerrado_en = now(), cerrado_por = ?, total_registrado = ?,
                                 total_lote = ?, diferencia = ?, justificacion = ?
                WHERE id = ? AND estado = 'ABIERTO'""", cerradoPor, totalRegistrado, totalLote, diferencia,
                justificacion, turnoId) == 1;
    }

    @Override
    public Optional<CierreDeTurno> cierre(long turnoId) {
        return jdbc.query(SELECT_CIERRE + "  AND t.id = ?", CIERRE, turnoId).stream().findFirst();
    }

    @Override
    public PaginaCierres buscarCierres(FiltrosCierres filtros) {
        StringBuilder condiciones = new StringBuilder();
        List<Object> argumentos = new ArrayList<>();
        if (filtros.desde() != null) {
            condiciones.append("  AND t.cerrado_en >= (?::date)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            argumentos.add(filtros.desde());
        }
        if (filtros.hasta() != null) {
            condiciones.append("  AND t.cerrado_en < (?::date + 1)::timestamp AT TIME ZONE 'America/Guatemala'\n");
            argumentos.add(filtros.hasta());
        }
        if (filtros.cajaId() != null) {
            condiciones.append("  AND t.caja_id = ?\n");
            argumentos.add(filtros.cajaId());
        }
        if (filtros.cajeroId() != null) {
            condiciones.append("  AND t.usuario_id = ?\n");
            argumentos.add(filtros.cajeroId());
        }
        if (filtros.soloConDiferencias()) {
            condiciones.append("  AND t.diferencia <> 0\n");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM turno t WHERE t.estado = 'CERRADO'\n" + condiciones,
                Long.class, argumentos.toArray());
        List<Object> pagina = new ArrayList<>(argumentos);
        pagina.add(FiltrosCierres.TAMANO_PAGINA);
        pagina.add((long) filtros.pagina() * FiltrosCierres.TAMANO_PAGINA);
        List<CierreDeTurno> cierres = jdbc.query(SELECT_CIERRE + condiciones
                + "ORDER BY t.cerrado_en DESC, t.id DESC LIMIT ? OFFSET ?", CIERRE, pagina.toArray());
        return new PaginaCierres(cierres, filtros.pagina(), FiltrosCierres.TAMANO_PAGINA, total);
    }

    @Override
    public List<OpcionFiltro> cajasConCierres() {
        return jdbc.query("""
                SELECT c.id, c.nombre, c.activo FROM caja c
                WHERE EXISTS (SELECT 1 FROM turno t WHERE t.caja_id = c.id AND t.estado = 'CERRADO')
                ORDER BY lower(c.nombre)""", (rs, i) -> new OpcionFiltro(rs.getLong("id"),
                rs.getString("nombre") + (rs.getBoolean("activo") ? "" : " (inactiva)")));
    }

    @Override
    public List<OpcionFiltro> cajerosConCierres() {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.username FROM usuario u
                WHERE EXISTS (SELECT 1 FROM turno t WHERE t.usuario_id = u.id AND t.estado = 'CERRADO')
                ORDER BY u.nombre_completo, u.username""", (rs, i) -> new OpcionFiltro(rs.getLong("id"),
                rs.getString("nombre_completo") + " (" + rs.getString("username") + ")"));
    }

    @Override
    public List<CierreDeTurno> cierres(Long usuarioId, int limite) {
        return usuarioId == null
                ? jdbc.query(SELECT_CIERRE + "ORDER BY t.cerrado_en DESC, t.id DESC LIMIT ?", CIERRE, limite)
                : jdbc.query(SELECT_CIERRE + "  AND t.usuario_id = ? ORDER BY t.cerrado_en DESC, t.id DESC LIMIT ?",
                        CIERRE, usuarioId, limite);
    }
}
