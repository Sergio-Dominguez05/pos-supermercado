package gt.supermercado.pos.catalogo.dominio;

import java.math.BigDecimal;

/**
 * Producto del catálogo (HU-03.01).
 *
 * @param precio precio de venta con IVA incluido, con 2 decimales
 */
public record Producto(long id, String codigoBarras, String descripcion, BigDecimal precio, boolean activo) {
}
