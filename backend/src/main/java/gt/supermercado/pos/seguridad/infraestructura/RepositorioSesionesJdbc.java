package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.RepositorioSesiones;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
class RepositorioSesionesJdbc implements RepositorioSesiones {

    private final JdbcTemplate jdbc;

    RepositorioSesionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long abrir(byte[] tokenHash, long usuarioId) {
        // La copia de permisos se arma en la base de datos, a partir del rol actual del usuario.
        return jdbc.queryForObject("""
                        INSERT INTO sesion (token_hash, usuario_id, permisos)
                        SELECT ?, u.id, coalesce(array_agg(rp.permiso ORDER BY rp.permiso)
                                                 FILTER (WHERE rp.permiso IS NOT NULL), '{}')
                        FROM usuario u LEFT JOIN rol_permiso rp ON rp.rol_id = u.rol_id
                        WHERE u.id = ?
                        GROUP BY u.id
                        RETURNING id""",
                Long.class, tokenHash, usuarioId);
    }

    @Override
    public Optional<SesionActiva> buscarActiva(byte[] tokenHash) {
        return jdbc.query("""
                        SELECT s.id AS sesion_id, u.id AS usuario_id, u.username, u.nombre_completo,
                               r.codigo AS rol, r.nombre AS rol_nombre, u.credencial_temporal, s.permisos
                        FROM sesion s
                        JOIN usuario u ON u.id = s.usuario_id
                        JOIN rol r ON r.id = u.rol_id
                        WHERE s.token_hash = ? AND s.cerrada_en IS NULL AND u.activo""",
                (rs, fila) -> new SesionActiva(
                        rs.getLong("sesion_id"),
                        rs.getLong("usuario_id"),
                        rs.getString("username"),
                        rs.getString("nombre_completo"),
                        Rol.valueOf(rs.getString("rol")),
                        rs.getString("rol_nombre"),
                        rs.getBoolean("credencial_temporal"),
                        Set.of((String[]) rs.getArray("permisos").getArray())),
                tokenHash).stream().findFirst();
    }
}
