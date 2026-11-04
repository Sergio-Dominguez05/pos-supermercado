package gt.supermercado.pos.seguridad.dominio;

/** Usuario tal como está guardado. {@code credencialHash} es el hash Argon2id, nunca la contraseña o el PIN. */
public record Usuario(
        long id,
        String username,
        String nombreCompleto,
        Rol rol,
        String credencialHash,
        boolean credencialTemporal,
        boolean activo) {
}
