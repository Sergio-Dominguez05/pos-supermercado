package gt.supermercado.pos.seguridad.dominio;

import java.util.Set;

/**
 * Sesión abierta de un usuario activo. Los permisos son la copia tomada al iniciar sesión,
 * de modo que un cambio de rol se aplica en el siguiente inicio de sesión (HU-01.07 crit. 4).
 */
public record SesionActiva(
        long sesionId,
        long usuarioId,
        String username,
        String nombreCompleto,
        Rol rol,
        String rolNombre,
        boolean credencialTemporal,
        Set<String> permisos) {
}
