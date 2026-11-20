package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.RepositorioPermisos;
import gt.supermercado.pos.seguridad.dominio.Rol;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class RepositorioPermisosJdbc implements RepositorioPermisos {

    private final JdbcTemplate jdbc;

    RepositorioPermisosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PermisoDelCatalogo> catalogo() {
        return jdbc.query("""
                SELECT p.codigo, p.modulo, p.descripcion, p.exclusivo_admin,
                       coalesce(array_agg(r.codigo ORDER BY r.id) FILTER (WHERE r.codigo IS NOT NULL), '{}') AS roles
                FROM permiso p
                LEFT JOIN rol_permiso rp ON rp.permiso = p.codigo
                LEFT JOIN rol r ON r.id = rp.rol_id
                GROUP BY p.codigo, p.modulo, p.descripcion, p.exclusivo_admin
                ORDER BY p.modulo, p.descripcion""", (rs, i) -> {
            Set<Rol> roles = Arrays.stream((String[]) rs.getArray("roles").getArray()).map(Rol::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Rol.class)));
            return new PermisoDelCatalogo(rs.getString("codigo"), rs.getString("modulo"), rs.getString("descripcion"),
                    rs.getBoolean("exclusivo_admin"), roles);
        });
    }

    @Override
    public Set<String> delRol(Rol rol) {
        return new HashSet<>(jdbc.queryForList("SELECT permiso FROM rol_permiso WHERE rol_id = ?", String.class,
                rol.id()));
    }

    @Override
    public void reemplazar(Rol rol, Set<String> permisos) {
        Set<String> actuales = delRol(rol);
        for (String quitado : actuales) {
            if (!permisos.contains(quitado)) {
                jdbc.update("DELETE FROM rol_permiso WHERE rol_id = ? AND permiso = ?", rol.id(), quitado);
            }
        }
        for (String agregado : permisos) {
            if (!actuales.contains(agregado)) {
                jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, ?)", rol.id(), agregado);
            }
        }
    }
}
