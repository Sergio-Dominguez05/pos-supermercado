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
    public long abrir(byte[] tokenHash, long usuarioId, Long cajaId) {
        // La copia del rol y de sus permisos se arma en la base de datos, a partir del rol actual del usuario.
        return jdbc.queryForObject("""
                        INSERT INTO sesion (token_hash, usuario_id, caja_id, rol_id, permisos)
                        SELECT ?, u.id, ?, u.rol_id, coalesce(array_agg(rp.permiso ORDER BY rp.permiso)
                                                 FILTER (WHERE rp.permiso IS NOT NULL), '{}')
                        FROM usuario u LEFT JOIN rol_permiso rp ON rp.rol_id = u.rol_id
                        WHERE u.id = ?
                        GROUP BY u.id
                        RETURNING id""",
                Long.class, tokenHash, cajaId, usuarioId);
    }

    @Override
    public void cerrar(long sesionId) {
        jdbc.update("UPDATE sesion SET cerrada_en = now() WHERE id = ? AND cerrada_en IS NULL", sesionId);
    }

    @Override
    public int cerrarTodasDe(long usuarioId) {
        return jdbc.update("UPDATE sesion SET cerrada_en = now() WHERE usuario_id = ? AND cerrada_en IS NULL", usuarioId);
    }

    @Override
    public Optional<SesionActiva> buscarActiva(byte[] tokenHash) {
        return jdbc.query("""
                        SELECT s.id AS sesion_id, u.id AS usuario_id, u.username, u.nombre_completo,
                               r.codigo AS rol, r.nombre AS rol_nombre, u.credencial_temporal, s.permisos,
                               s.caja_id, c.nombre AS caja_nombre
                        FROM sesion s
                        JOIN usuario u ON u.id = s.usuario_id
                        JOIN rol r ON r.id = s.rol_id   -- el rol de cuando inició sesión (HU-01.07 crit. 4)
                        LEFT JOIN caja c ON c.id = s.caja_id
                        WHERE s.token_hash = ? AND s.cerrada_en IS NULL AND u.activo""",
                (rs, fila) -> new SesionActiva(
                        rs.getLong("sesion_id"),
                        rs.getLong("usuario_id"),
                        rs.getString("username"),
                        rs.getString("nombre_completo"),
                        Rol.valueOf(rs.getString("rol")),
                        rs.getString("rol_nombre"),
                        rs.getBoolean("credencial_temporal"),
                        Set.of((String[]) rs.getArray("permisos").getArray()),
                        rs.getObject("caja_id", Long.class),
                        rs.getString("caja_nombre")),
                tokenHash).stream().findFirst();
    }
}
