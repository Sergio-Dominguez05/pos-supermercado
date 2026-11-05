package gt.supermercado.pos.seguridad.dominio;

import java.util.Optional;

public interface RepositorioSesiones {

    /** Abre una sesión con una copia de los permisos actuales del rol del usuario. Devuelve su id. */
    long abrir(byte[] tokenHash, long usuarioId);

    /** Sesión no cerrada cuyo usuario sigue activo. */
    Optional<SesionActiva> buscarActiva(byte[] tokenHash);

    /** Marca la sesión como cerrada con la hora del servidor; el token deja de valer (HU-01.16). */
    void cerrar(long sesionId);
}
