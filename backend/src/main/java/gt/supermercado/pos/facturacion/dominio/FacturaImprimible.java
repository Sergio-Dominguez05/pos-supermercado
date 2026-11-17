package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;

/**
 * HU-08.05: la factura con la copia del receptor, del emisor y del establecimiento que se guardó al emitirla. Cambiar
 * la configuración después no altera la factura impresa ni sus reimpresiones (HU-08.12).
 */
public record FacturaImprimible(Factura factura, Receptor receptor, Emisor emisor, Establecimiento establecimiento) {
}
