package gt.supermercado.pos.seguridad.dominio;

import java.time.OffsetDateTime;

/**
 * Lo que la lista de usuarios puede mostrar (HU-01.06 crit. 1). No incluye la credencial ni su hash (crit. 4).
 *
 * @param ultimoAcceso {@code null} si el usuario nunca ha iniciado sesión
 */
public record ResumenUsuario(
        long id,
        String username,
        String nombreCompleto,
        Rol rol,
        String rolNombre,
        boolean activo,
        OffsetDateTime ultimoAcceso) {
}
