package gt.supermercado.pos.pagos.dominio;

import gt.supermercado.pos.ventas.dominio.Venta;

/**
 * HU-07.08 crit. 1: al finalizar el cobro se emite la factura de la venta (EP-08). Lo implementa el módulo
 * {@code facturacion} (HU-08.03) en dos pasos, como pide Architecture.md §6.2:
 * <ol>
 *   <li>{@link #emitir(Venta)}, dentro de la transacción que finaliza la venta: registra la factura {@code PENDIENTE}
 *       y la tarea de certificarla. Si lanza una excepción, la venta no queda finalizada a medias (crit. 4).</li>
 *   <li>{@link #certificar(Venta)}, después del {@code COMMIT}: envía la factura al certificador. Nunca lanza: si el
 *       certificador no responde, la factura queda en contingencia y la venta ya está guardada.</li>
 * </ol>
 */
public interface EmisionDeFactura {

    /** Registra la factura pendiente de la venta recién finalizada. Lanza {@code NegocioException} si no se puede. */
    void emitir(Venta venta);

    /** Certifica la factura de la venta y devuelve cómo quedó. */
    FacturaDeVenta certificar(Venta venta);
}
