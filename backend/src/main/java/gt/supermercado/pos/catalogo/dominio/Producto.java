package gt.supermercado.pos.catalogo.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Producto del catálogo (HU-03.01).
 *
 * @param precio        precio de venta con IVA incluido, con 2 decimales
 * @param modificadoEn  fecha de la última modificación: alta, edición o cambio de estado (HU-03.04 crit. 1)
 */
public record Producto(long id, String codigoBarras, String descripcion, BigDecimal precio, boolean activo,
                       OffsetDateTime modificadoEn) {
}
