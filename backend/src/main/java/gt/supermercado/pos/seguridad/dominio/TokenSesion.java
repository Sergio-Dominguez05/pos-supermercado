package gt.supermercado.pos.seguridad.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token opaco de sesión. Al cliente se le entrega el token; la base de datos solo guarda su SHA-256,
 * así una copia de la base de datos no sirve para suplantar sesiones.
 */
public final class TokenSesion {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenSesion() {
    }

    public static String generar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
