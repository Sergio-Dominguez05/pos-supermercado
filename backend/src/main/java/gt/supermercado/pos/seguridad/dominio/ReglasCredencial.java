package gt.supermercado.pos.seguridad.dominio;

import java.util.Locale;
import java.util.regex.Pattern;

/** Formato del nombre de usuario, de la contraseña y del PIN (HU-01.02, HU-01.05). */
public final class ReglasCredencial {

    /** De 3 a 30 caracteres: letras minúsculas, números, punto, guion o guion bajo. */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,30}");
    private static final Pattern PIN = Pattern.compile("\\d{4,6}");
    private static final int LARGO_MAXIMO_CONTRASENA = 128;

    private ReglasCredencial() {
    }

    /** Los nombres de usuario no distinguen mayúsculas: se guardan y se buscan en minúsculas. */
    public static String normalizarUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    /** Recibe el nombre ya normalizado. */
    public static boolean esUsernameValido(String username) {
        return USERNAME.matcher(username).matches();
    }

    /** Al menos 8 caracteres, con letras y números (HU-01.05 crit. 2). */
    public static boolean esContrasenaValida(String contrasena) {
        return contrasena.length() >= 8
                && contrasena.length() <= LARGO_MAXIMO_CONTRASENA
                && contrasena.chars().anyMatch(Character::isLetter)
                && contrasena.chars().anyMatch(Character::isDigit);
    }

    /** Numérico, de 4 a 6 dígitos (HU-01.02 crit. 2, HU-01.05 crit. 3). */
    public static boolean esPinValido(String pin) {
        return PIN.matcher(pin).matches();
    }

    public static boolean esCredencialValida(TipoCredencial tipo, String credencial) {
        return tipo == TipoCredencial.PIN ? esPinValido(credencial) : esContrasenaValida(credencial);
    }

    /** Código de error estable para una credencial temporal que no cumple su regla. */
    public static String codigoCredencialInvalida(TipoCredencial tipo) {
        return tipo == TipoCredencial.PIN ? "PIN_INVALIDO" : "CONTRASENA_INVALIDA";
    }

    public static String mensajeCredencialInvalida(TipoCredencial tipo) {
        return tipo == TipoCredencial.PIN
                ? "El PIN temporal debe ser numérico, de 4 a 6 dígitos."
                : "La contraseña temporal debe tener al menos 8 caracteres, con letras y números.";
    }
}
