package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cálculo de IVA de una línea de venta (HU-04.03). Contiene los cinco datos que la línea debe guardar (crit. 1),
 * con los mismos decimales que {@code venta_linea} de Architecture.md §5.2:
 *
 * <ul>
 *   <li>{@code cantidad}: entero mayor que cero ({@code int}).</li>
 *   <li>{@code precioUnitario}: precio con IVA del catálogo, sin cambios ({@code numeric(12,2)}).</li>
 *   <li>{@code subtotalConIva}, {@code base} e {@code iva}: 6 decimales, sin redondear a 2
 *       ({@code numeric(18,6)}, crit. 2).</li>
 * </ul>
 *
 * <p>Siempre se cumple {@code base + iva = subtotalConIva}.
 */
public record LineaCalculada(
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotalConIva,
        BigDecimal base,
        BigDecimal iva) {

    public LineaCalculada {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser un entero mayor que cero: " + cantidad);
        }
        Objects.requireNonNull(precioUnitario, "El precio unitario es obligatorio.");
        Objects.requireNonNull(subtotalConIva, "El subtotal con IVA es obligatorio.");
        Objects.requireNonNull(base, "La base imponible es obligatoria.");
        Objects.requireNonNull(iva, "El IVA es obligatorio.");
    }
}
