package gt.supermercado.pos.seguridad.dominio;

import gt.supermercado.pos.compartido.dominio.NoAutenticadoException;

/**
 * Inicio de sesión rechazado. El mensaje es siempre el mismo para cada tipo de credencial, sin decir si falló
 * el usuario, la credencial o si el usuario está desactivado (HU-01.01 crit. 2 y 3, HU-01.02 crit. 4 y 5).
 */
public class CredencialesInvalidasException extends NoAutenticadoException {

    public CredencialesInvalidasException(TipoCredencial tipo) {
        super("CREDENCIALES_INVALIDAS", "Usuario o " + tipo.nombre() + " incorrectos");
    }
}
