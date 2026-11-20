package gt.supermercado.pos.compartido.dominio;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Una dirección de correo con la forma de {@code nombre@dominio.tld}, la misma regla que validan las columnas de
 * correo de la base de datos (HU-05.05, HU-05.11 crit. 3, HU-08.15).
 */
public final class Correo {

    public static final int LARGO_MAXIMO = 254;

    private static final Pattern FORMATO = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private Correo() {
    }

    /** La dirección sin espacios alrededor, o vacío si está en blanco. Lanza si no tiene el formato. */
    public static Optional<String> opcional(String valor, String codigoError) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String correo = valor.strip();
        if (correo.length() > LARGO_MAXIMO || !FORMATO.matcher(correo).matches()) {
            throw new NegocioException(codigoError, "El correo no es válido, por ejemplo cliente@correo.com.");
        }
        return Optional.of(correo);
    }
}
