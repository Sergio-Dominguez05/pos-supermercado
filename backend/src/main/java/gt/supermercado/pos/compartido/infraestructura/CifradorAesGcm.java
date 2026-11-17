package gt.supermercado.pos.compartido.infraestructura;

import gt.supermercado.pos.compartido.dominio.CifradorSecretos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM con un vector de inicialización aleatorio por secreto (Architecture.md §7.5). Se guarda
 * {@code vector (12 bytes) || texto cifrado con su etiqueta}: GCM detecta cualquier alteración al descifrar.
 *
 * <p>La llave maestra ({@code pos.seguridad.llave-maestra}, 32 bytes en Base64) viene de la variable de entorno
 * {@code POS_LLAVE_MAESTRA} en producción. Sin llave, la aplicación arranca, pero no guarda ni lee credenciales.
 */
@Component
class CifradorAesGcm implements CifradorSecretos {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int BYTES_VECTOR = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKeySpec llave;
    private final SecureRandom aleatorio = new SecureRandom();

    CifradorAesGcm(@Value("${pos.seguridad.llave-maestra:}") String llaveBase64) {
        this.llave = llaveBase64.isBlank() ? null : llave(llaveBase64.strip());
    }

    private static SecretKeySpec llave(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La llave maestra (POS_LLAVE_MAESTRA) no está en Base64.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("La llave maestra (POS_LLAVE_MAESTRA) debe tener 32 bytes (AES-256).");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    @Override
    public byte[] cifrar(String secreto) {
        byte[] vector = new byte[BYTES_VECTOR];
        aleatorio.nextBytes(vector);
        try {
            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.ENCRYPT_MODE, exigirLlave(), new GCMParameterSpec(BITS_ETIQUETA, vector));
            byte[] cifrado = cifrador.doFinal(secreto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(vector.length + cifrado.length).put(vector).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto.", e);
        }
    }

    @Override
    public String descifrar(byte[] cifrado) {
        if (cifrado.length <= BYTES_VECTOR) {
            throw new IllegalStateException("El secreto guardado no es válido.");
        }
        try {
            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.DECRYPT_MODE, exigirLlave(),
                    new GCMParameterSpec(BITS_ETIQUETA, cifrado, 0, BYTES_VECTOR));
            byte[] claro = cifrador.doFinal(cifrado, BYTES_VECTOR, cifrado.length - BYTES_VECTOR);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto: se alteró o se cifró con otra llave.", e);
        }
    }

    private SecretKeySpec exigirLlave() {
        if (llave == null) {
            throw new IllegalStateException("Falta la llave maestra (POS_LLAVE_MAESTRA) para cifrar las credenciales.");
        }
        return llave;
    }
}
