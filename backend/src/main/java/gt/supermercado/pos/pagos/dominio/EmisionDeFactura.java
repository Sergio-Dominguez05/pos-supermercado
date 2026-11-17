package gt.supermercado.pos.pagos.dominio;

import gt.supermercado.pos.ventas.dominio.Venta;

/**
 * HU-07.08 crit. 1: al finalizar el cobro se emite la factura de la venta (EP-08). Se llama dentro de la transacción
 * que finaliza la venta: si lanza una excepción, la venta no queda finalizada a medias (crit. 4).
 *
 * <p>La facturación (EP-08) todavía no existe: mientras tanto rige {@code SinFacturaTodavia}, que no emite nada.
 * HU-08.03 (#66) debe implementar este puerto en el módulo {@code facturacion} y quitar esa clase. Según
 * Architecture.md §6.2, aquí solo se registra la factura {@code PENDIENTE} y la tarea de certificarla; la llamada al
 * certificador se hace después del {@code COMMIT}.
 */
public interface EmisionDeFactura {

    /** Emite la factura de la venta recién finalizada. Devuelve {@code false} si no se emitió ninguna. */
    boolean emitir(Venta venta);
}
