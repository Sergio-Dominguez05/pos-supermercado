package gt.supermercado.pos.pagos.dominio;

import gt.supermercado.pos.ventas.dominio.Venta;

import java.util.List;

/**
 * HU-08.05: al terminar el cobro, la factura certificada se imprime en la impresora de la caja. Lo implementa el
 * módulo {@code impresion}. Se llama después de certificar, fuera de la transacción de la venta: si la impresora falla,
 * la venta y su factura siguen siendo válidas y la factura se puede reimprimir (crit. 6, HU-08.12). Nunca lanza.
 */
public interface ImpresionDeFactura {

    ResultadoImpresion imprimir(Venta venta, List<Pago> pagos, FacturaDeVenta factura);
}
