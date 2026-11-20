package gt.supermercado.pos.facturacion.dominio;

import java.util.Optional;

/**
 * A quién se factura la venta (HU-08.02 crit. 1): el cliente que se le asignó (EP-05) o Consumidor Final. Lo
 * implementa el módulo {@code clientes}.
 */
public interface ReceptorDeVenta {

    Receptor de(long ventaId);

    /** HU-05.11: el correo al que se envía la factura de la venta (HU-08.15), o vacío si no se envía. */
    Optional<String> correoDeFactura(long ventaId);
}
