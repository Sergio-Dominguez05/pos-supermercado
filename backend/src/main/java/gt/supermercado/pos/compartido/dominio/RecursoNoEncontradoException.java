package gt.supermercado.pos.compartido.dominio;

/** El registro pedido no existe. La API responde 404 en lugar de 422. */
public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
