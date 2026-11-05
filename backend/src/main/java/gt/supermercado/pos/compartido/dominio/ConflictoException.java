package gt.supermercado.pos.compartido.dominio;

/** La operación no se puede hacer en el estado actual; el usuario debe resolver algo antes. La API responde 409. */
public class ConflictoException extends NegocioException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
