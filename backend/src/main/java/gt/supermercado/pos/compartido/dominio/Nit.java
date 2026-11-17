package gt.supermercado.pos.compartido.dominio;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HU-05.03: el NIT guatemalteco. Lo usan el emisor (HU-12.01 crit. 2) y, con EP-05, los clientes.
 * <ul>
 *   <li>Crit. 1: el último carácter es el dígito verificador según el algoritmo de la SAT (módulo 11; "K" vale 10).</li>
 *   <li>Crit. 3: se guarda en un formato uniforme: sin guiones ni espacios y en mayúsculas.</li>
 * </ul>
 */
public final class Nit {

    /** De 2 a 13 caracteres: al menos un dígito y el verificador. */
    private static final Pattern FORMATO = Pattern.compile("\\d{1,12}[0-9K]");

    private Nit() {
    }

    /** El NIT en formato uniforme, sin validar el dígito verificador. */
    public static String normalizar(String nit) {
        return nit == null ? "" : nit.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** El NIT normalizado, solo si su dígito verificador es correcto. */
    public static Optional<String> validar(String nit) {
        String normal = normalizar(nit);
        if (!FORMATO.matcher(normal).matches()) {
            return Optional.empty();
        }
        String cuerpo = normal.substring(0, normal.length() - 1);
        return normal.charAt(normal.length() - 1) == verificador(cuerpo) ? Optional.of(normal) : Optional.empty();
    }

    public static boolean esValido(String nit) {
        return validar(nit).isPresent();
    }

    /**
     * Módulo 11 de la SAT: cada dígito se multiplica por su posición contada desde la derecha más uno (el último del
     * cuerpo por 2, el anterior por 3...); el verificador es {@code (11 - suma mod 11) mod 11}, y 10 se escribe "K".
     */
    static char verificador(String cuerpo) {
        int suma = 0;
        int factor = cuerpo.length() + 1;
        for (char digito : cuerpo.toCharArray()) {
            suma += (digito - '0') * factor--;
        }
        int resultado = (11 - suma % 11) % 11;
        return resultado == 10 ? 'K' : (char) ('0' + resultado);
    }
}
