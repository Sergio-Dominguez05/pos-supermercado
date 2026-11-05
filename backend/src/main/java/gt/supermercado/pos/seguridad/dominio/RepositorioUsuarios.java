package gt.supermercado.pos.seguridad.dominio;

import java.util.Optional;

public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorUsername(String username);

    Optional<Usuario> buscarPorId(long id);

    boolean existeAlgunoConRol(Rol rol);

    long contarAdministradoresActivos();

    /**
     * Cambia el nombre completo y el rol (HU-01.07). El nombre de usuario nunca cambia.
     *
     * @param credencialHash nueva credencial temporal (al pasar entre Cajero y los demás roles),
     *                       o {@code null} para conservar la actual
     */
    void actualizar(long id, String nombreCompleto, Rol rol, String credencialHash);

    /** Guarda una credencial definitiva elegida por el propio usuario (HU-01.13): deja de ser temporal. */
    void cambiarCredencial(long id, String credencialHash);

    /** Activa o desactiva; los usuarios nunca se borran (HU-01.08 crit. 2). */
    void cambiarActivo(long id, boolean activo);

    /** Crea el usuario activo y con credencial temporal. Devuelve su id. */
    long crear(String username, String nombreCompleto, Rol rol, String credencialHash);

    /** Guarda la hora del servidor como último acceso. */
    void registrarAcceso(long usuarioId);
}
