package gt.supermercado.pos.seguridad.dominio;

import java.util.Set;

/**
 * Sesión abierta de un usuario activo. El rol y los permisos son la copia tomada al iniciar sesión,
 * de modo que un cambio de rol se aplica en el siguiente inicio de sesión (HU-01.07 crit. 4).
 *
 * @param cajaId     caja del equipo desde el que se inició sesión (HU-12.05 crit. 4), o {@code null} si el equipo
 *                   no está vinculado a una caja (por ejemplo, la PC de administración)
 * @param cajaNombre nombre de esa caja, o {@code null}
 */
public record SesionActiva(
        long sesionId,
        long usuarioId,
        String username,
        String nombreCompleto,
        Rol rol,
        String rolNombre,
        boolean credencialTemporal,
        Set<String> permisos,
        Long cajaId,
        String cajaNombre) {

    /** Sesión iniciada desde un equipo sin caja. */
    public SesionActiva(long sesionId, long usuarioId, String username, String nombreCompleto, Rol rol,
                        String rolNombre, boolean credencialTemporal, Set<String> permisos) {
        this(sesionId, usuarioId, username, nombreCompleto, rol, rolNombre, credencialTemporal, permisos, null, null);
    }
}
