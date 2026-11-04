package gt.supermercado.pos.compartido.dominio;

/** La identidad del usuario no se pudo comprobar. La API responde 401 en lugar de 422. */
public class NoAutenticadoException extends NegocioException {

    public NoAutenticadoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
