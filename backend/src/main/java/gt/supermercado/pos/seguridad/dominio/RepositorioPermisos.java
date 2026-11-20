package gt.supermercado.pos.seguridad.dominio;

import java.util.List;
import java.util.Set;

/** HU-01.10: el catálogo de permisos y los que tiene cada rol (tablas {@code permiso} y {@code rol_permiso}). */
public interface RepositorioPermisos {

    /** Todos los permisos, con su módulo, si son exclusivos del Administrador y qué roles los tienen. */
    List<PermisoDelCatalogo> catalogo();

    Set<String> delRol(Rol rol);

    /** Deja al rol exactamente con estos permisos. */
    void reemplazar(Rol rol, Set<String> permisos);

    record PermisoDelCatalogo(String codigo, String modulo, String descripcion, boolean exclusivoAdmin,
                              Set<Rol> roles) {}
}
