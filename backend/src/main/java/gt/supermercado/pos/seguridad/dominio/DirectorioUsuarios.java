package gt.supermercado.pos.seguridad.dominio;

import java.util.List;

/** Consulta de usuarios para la lista de administración (HU-01.06). Solo lectura. */
public interface DirectorioUsuarios {

    /**
     * Usuarios ordenados por nombre completo.
     *
     * @param texto  busca en el nombre completo (sin distinguir tildes ni mayúsculas) o en el nombre de usuario;
     *               {@code null} para no filtrar
     * @param rol    {@code null} para todos los roles
     * @param activo {@code null} para activos e inactivos
     */
    List<ResumenUsuario> buscar(String texto, Rol rol, Boolean activo);
}
