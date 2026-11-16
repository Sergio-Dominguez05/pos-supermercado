package gt.supermercado.pos.ventas.dominio;

import java.math.BigDecimal;

/**
 * Una línea de la venta. Guarda una copia de la descripción y del precio vigente al agregarla (HU-06.09), y su
 * desglose de IVA con 6 decimales, sin redondear (HU-04.03).
 *
 * @param precioUnitario precio con IVA de una unidad, con 2 decimales
 */
public record LineaVenta(
        long id,
        long productoId,
        String codigoBarras,
        String descripcion,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotalConIva,
        BigDecimal baseImponible,
        BigDecimal iva) {
}
