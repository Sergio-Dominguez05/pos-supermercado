package gt.supermercado.pos.facturacion.dominio;

/**
 * A quién se factura la venta (HU-08.02 crit. 1): el cliente que se le asignó (EP-05) o Consumidor Final. Lo
 * implementa el módulo {@code clientes}.
 */
public interface ReceptorDeVenta {

    Receptor de(long ventaId);
}
