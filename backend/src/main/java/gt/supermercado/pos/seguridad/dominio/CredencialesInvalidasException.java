package gt.supermercado.pos.seguridad.dominio;

import gt.supermercado.pos.compartido.dominio.NoAutenticadoException;

/**
 * Inicio de sesión rechazado. El mensaje es siempre el mismo, sin decir si falló el usuario,
 * la contraseña o si el usuario está desactivado (HU-01.01 crit. 2 y 3).
 */
public class CredencialesInvalidasException extends NoAutenticadoException {

    public static final String MENSAJE = "Usuario o contraseña incorrectos";

    public CredencialesInvalidasException() {
        super("CREDENCIALES_INVALIDAS", MENSAJE);
    }
}
