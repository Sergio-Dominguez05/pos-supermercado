package gt.supermercado.pos.caja.infraestructura;

import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
    public long abrir(long cajaId, long usuarioId) {
        return jdbc.queryForObject("INSERT INTO turno (caja_id, usuario_id) VALUES (?, ?) RETURNING id",
                Long.class, cajaId, usuarioId);
    }
}
