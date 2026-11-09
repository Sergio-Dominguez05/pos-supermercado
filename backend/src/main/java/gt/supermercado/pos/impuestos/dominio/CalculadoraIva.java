package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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

    /**
     * Redondeo al medio hacia arriba (HU-04.05 crit. 3): si el primer decimal que se elimina es 5 o más, el anterior
     * sube en 1 (10.715 → 10.72; 10.714 → 10.71). Se usa solo al dividir para obtener la base imponible con
     * 6 decimales y al redondear los totales a 2. Reglas completas: {@code docs/arquitectura/calculo-iva.md}.
     */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Decimales de los totales del comprobante (Subtotal, IVA y TOTAL); coinciden con {@code numeric(12,2)}. */
    public static final int DECIMALES_TOTALES = 2;

    private CalculadoraIva() {
    }

    /**
     * Redondea un resultado final a 2 decimales al medio hacia arriba (HU-04.05 crit. 2 y 3). Los valores por línea y
     * las sumas intermedias nunca pasan por aquí: conservan sus 6 decimales (crit. 1).
     */
    public static BigDecimal redondearTotal(BigDecimal monto) {
        return Objects.requireNonNull(monto, "El monto es obligatorio.").setScale(DECIMALES_TOTALES, REDONDEO);
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

    /** Calcula una línea de venta con la tasa vigente del 12 % (HU-04.03). */
    public static LineaCalculada linea(BigDecimal precioUnitario, int cantidad) {
        return linea(precioUnitario, cantidad, TasaIva.VIGENTE);
    }

    /**
     * Calcula una línea de venta (HU-04.03): el subtotal con IVA es {@code precioUnitario × cantidad} (exacto) y se
     * desglosa una sola vez sobre ese subtotal, no por unidad, para no acumular redondeos.
     *
     * @param cantidad entero mayor que cero (HU-06.04 crit. 3)
     */
    public static LineaCalculada linea(BigDecimal precioUnitario, int cantidad, TasaIva tasa) {
        Objects.requireNonNull(precioUnitario, "El precio unitario es obligatorio.");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser un entero mayor que cero: " + cantidad);
        }
        BigDecimal subtotalConIva = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        DesgloseIva desglose = desglosar(subtotalConIva, tasa);
        return new LineaCalculada(cantidad, precioUnitario, subtotalConIva.setScale(DECIMALES, REDONDEO),
                desglose.base(), desglose.iva());
    }

    /**
     * Totales del comprobante (HU-04.04, Architecture.md §7.1). Las sumas se hacen con los 6 decimales de cada línea
     * y solo el resultado final se redondea a 2:
     *
     * <ul>
     *   <li>TOTAL = suma de los subtotales con IVA (crit. 4).</li>
     *   <li>IVA = suma del IVA de las líneas (crit. 3).</li>
     *   <li>Subtotal = TOTAL − IVA, para que Subtotal + IVA = TOTAL siempre (crit. 5). Coincide con la suma de las
     *       bases redondeada (crit. 2) salvo, en raros casos, por un centavo; la decisión D3 de Architecture.md §12.1
     *       acepta esa diferencia en la primera entrega.</li>
     * </ul>
     *
     * <p>Sin líneas, los tres totales son {@code 0.00}.
     */
    public static Totales totales(List<LineaCalculada> lineas) {
        Objects.requireNonNull(lineas, "Las líneas son obligatorias.");
        BigDecimal total = redondearTotal(suma(lineas, LineaCalculada::subtotalConIva));
        BigDecimal iva = redondearTotal(suma(lineas, LineaCalculada::iva));
        return new Totales(total.subtract(iva), iva, total);
    }

    private static BigDecimal suma(List<LineaCalculada> lineas, Function<LineaCalculada, BigDecimal> monto) {
        return lineas.stream().map(monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
