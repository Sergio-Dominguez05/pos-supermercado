package gt.supermercado.pos.facturacion.dominio;

/** HU-08.15 crit. 3: el servidor de correo no respondió o no aceptó el mensaje. El mensaje dice por qué. */
public class CorreoNoEnviadoException extends RuntimeException {

    public CorreoNoEnviadoException(String mensaje) {
        super(mensaje);
    }

    public CorreoNoEnviadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
