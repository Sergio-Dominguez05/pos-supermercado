package gt.supermercado.pos.impresion.dominio;

/**
 * La impresora no respondió: está apagada, sin papel o fuera de la red. HU-08.05 crit. 6: la factura sigue siendo
 * válida y se puede reimprimir (HU-08.12).
 */
public class ImpresoraNoDisponibleException extends RuntimeException {

    public ImpresoraNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
