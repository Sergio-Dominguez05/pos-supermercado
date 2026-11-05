package gt.supermercado.pos.seguridad.dominio;

import java.util.Optional;

public interface RepositorioSesiones {

    /**
     * Abre una sesión con una copia del rol y los permisos actuales del usuario. Devuelve su id.
     *
     * @param cajaId caja del equipo desde el que se inicia (HU-12.05), o {@code null}
     */
    long abrir(byte[] tokenHash, long usuarioId, Long cajaId);

    /** Sesión no cerrada cuyo usuario sigue activo. */
    Optional<SesionActiva> buscarActiva(byte[] tokenHash);

    /** Marca la sesión como cerrada con la hora del servidor; el token deja de valer (HU-01.16). */
    void cerrar(long sesionId);
}
