package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Totales del comprobante (HU-04.04), en el orden en que se muestran en la pantalla de venta y en el ticket
 * (crit. 1): Subtotal, IVA y TOTAL. Los tres tienen 2 decimales y siempre {@code subtotal + iva = total} (crit. 5).
 */
public record Totales(BigDecimal subtotal, BigDecimal iva, BigDecimal total) {

    public Totales {
        Objects.requireNonNull(subtotal, "El subtotal es obligatorio.");
        Objects.requireNonNull(iva, "El IVA es obligatorio.");
        Objects.requireNonNull(total, "El total es obligatorio.");
    }
}
