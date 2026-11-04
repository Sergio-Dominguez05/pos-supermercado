package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private final JdbcTemplate jdbc;

    RepositorioUsuariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return jdbc.query("""
                        SELECT u.id, u.username, u.nombre_completo, r.codigo AS rol,
                               u.credencial_hash, u.credencial_temporal, u.activo
                        FROM usuario u JOIN rol r ON r.id = u.rol_id
                        WHERE u.username = ?""",
                (rs, fila) -> new Usuario(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nombre_completo"),
                        Rol.valueOf(rs.getString("rol")),
                        rs.getString("credencial_hash"),
                        rs.getBoolean("credencial_temporal"),
                        rs.getBoolean("activo")),
                username).stream().findFirst();
    }

    @Override
    public boolean existeAlgunoConRol(Rol rol) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario WHERE rol_id = ?)", Boolean.class, rol.id()));
    }

    @Override
    public long crear(String username, String nombreCompleto, Rol rol, String credencialHash) {
        return jdbc.queryForObject("""
                        INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash)
                        VALUES (?, ?, ?, ?) RETURNING id""",
                Long.class, username, nombreCompleto, rol.id(), credencialHash);
    }

    @Override
    public void registrarAcceso(long usuarioId) {
        jdbc.update("UPDATE usuario SET ultimo_acceso = now() WHERE id = ?", usuarioId);
    }
}
