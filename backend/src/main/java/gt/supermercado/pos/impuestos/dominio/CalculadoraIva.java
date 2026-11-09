package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cálculo puro del IVA (Architecture.md §7.1). No depende de Spring ni de la base de datos.
 *
 * <p>HU-04.02: los precios del catálogo ya incluyen el IVA, así que la base imponible se obtiene dividiendo entre
 * {@code 1 + tasa} y el IVA es la diferencia:
 *
 * <pre>
 *   base = precioConIva / 1.12   (6 decimales, HALF_UP)
 *   iva  = precioConIva - base
 * </pre>
 *
 * <p>Todo se calcula con {@link BigDecimal}; nunca con {@code float} ni {@code double} (crit. 4).
 */
public final class CalculadoraIva {

    /** Decimales de los montos intermedios; coinciden con {@code numeric(18,6)} de {@code venta_linea}. */
    public static final int DECIMALES = 6;

    /** Redondeo de los montos intermedios. */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private CalculadoraIva() {
    }

    /** Desglosa un precio con IVA usando la tasa vigente del 12 %. */
    public static DesgloseIva desglosar(BigDecimal precioConIva) {
        return desglosar(precioConIva, TasaIva.VIGENTE);
    }

    /**
     * Desglosa un precio con IVA usando una tasa dada, por ejemplo la guardada en una venta pasada (HU-04.01 crit. 3).
     * El precio recibido se devuelve tal cual en el resultado.
     */
    public static DesgloseIva desglosar(BigDecimal precioConIva, TasaIva tasa) {
        Objects.requireNonNull(precioConIva, "El precio con IVA es obligatorio.");
        Objects.requireNonNull(tasa, "La tasa de IVA es obligatoria.");

        BigDecimal base = precioConIva.divide(tasa.divisor(), DECIMALES, REDONDEO);
        BigDecimal iva = precioConIva.setScale(DECIMALES, REDONDEO).subtract(base);
        return new DesgloseIva(precioConIva, base, iva);
    }
}
