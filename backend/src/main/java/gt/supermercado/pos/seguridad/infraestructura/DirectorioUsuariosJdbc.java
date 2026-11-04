package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.DirectorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class DirectorioUsuariosJdbc implements DirectorioUsuarios {

    // La consulta nunca selecciona credencial_hash (HU-01.06 crit. 4).
    private static final String SELECT_RESUMEN = """
            SELECT u.id, u.username, u.nombre_completo, r.codigo AS rol, r.nombre AS rol_nombre,
                   u.activo, u.ultimo_acceso
            FROM usuario u JOIN rol r ON r.id = u.rol_id
            """;

    private static final RowMapper<ResumenUsuario> RESUMEN = (rs, fila) -> new ResumenUsuario(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("nombre_completo"),
            Rol.valueOf(rs.getString("rol")),
            rs.getString("rol_nombre"),
            rs.getBoolean("activo"),
            rs.getObject("ultimo_acceso", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    DirectorioUsuariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ResumenUsuario> buscar(String texto, Rol rol, Boolean activo) {
        StringBuilder sql = new StringBuilder(SELECT_RESUMEN).append("WHERE true");
        List<Object> argumentos = new ArrayList<>();
        if (texto != null) {
            // Igual que la búsqueda de productos (HU-03.07): sin distinguir tildes ni mayúsculas.
            sql.append(" AND (f_unaccent(lower(u.nombre_completo)) LIKE '%' || f_unaccent(lower(?)) || '%'")
                    .append(" OR u.username LIKE '%' || lower(?) || '%')");
            String patron = escaparLike(texto);
            argumentos.add(patron);
            argumentos.add(patron);
        }
        if (rol != null) {
            sql.append(" AND r.codigo = ?");
            argumentos.add(rol.name());
        }
        if (activo != null) {
            sql.append(" AND u.activo = ?");
            argumentos.add(activo);
        }
        sql.append(" ORDER BY lower(u.nombre_completo), u.username");

        return jdbc.query(sql.toString(), RESUMEN, argumentos.toArray());
    }

    @Override
    public Optional<ResumenUsuario> buscarPorId(long id) {
        return jdbc.query(SELECT_RESUMEN + "WHERE u.id = ?", RESUMEN, id).stream().findFirst();
    }

    /** Lo que escribe el usuario se busca literal: '%' y '_' no son comodines (p. ej. en "caja_1"). */
    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
