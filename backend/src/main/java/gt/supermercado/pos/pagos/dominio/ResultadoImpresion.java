package gt.supermercado.pos.pagos.dominio;

/**
 * Cómo terminó la impresión de la factura (HU-08.05).
 *
 * @param impresa {@code true} si se envió a la impresora
 * @param mensaje qué decirle al cajero si no se imprimió; {@code null} si no hay nada que decir (por ejemplo, la
 *                factura quedó en contingencia y el aviso de la factura ya lo explica)
 */
public record ResultadoImpresion(boolean impresa, String mensaje) {

    public static ResultadoImpresion enviada() {
        return new ResultadoImpresion(true, null);
    }

    public static ResultadoImpresion sinImprimir(String mensaje) {
        return new ResultadoImpresion(false, mensaje);
    }
}
