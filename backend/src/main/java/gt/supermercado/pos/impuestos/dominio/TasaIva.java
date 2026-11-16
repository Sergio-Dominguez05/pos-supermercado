package gt.supermercado.pos.impuestos.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tasa del IVA (HU-04.01, Architecture.md §7.1).
 *
 * <ul>
 *   <li>Crit. 1: todos los productos están gravados con la misma tasa; no existen productos exentos, así que el
 *       sistema no tiene ninguna otra tasa ni una marca de exención por producto.</li>
 *   <li>Crit. 2: la tasa vigente es una constante del sistema ({@link #VIGENTE}), no un parámetro de configuración;
 *       ninguna pantalla ni endpoint la puede cambiar.</li>
 *   <li>Crit. 3: cada venta guarda la tasa con la que se calculó ({@code venta.tasa_iva numeric(5,4)}, que llega con
 *       HU-06.01). {@link #de(BigDecimal)} reconstruye esa tasa guardada para que las ventas pasadas se sigan
 *       calculando igual aunque la tasa vigente cambie en una versión futura.</li>
 * </ul>
 *
 * <p>El valor es una fracción ({@code 0.1200} = 12 %) con exactamente 4 decimales, igual que la columna donde se
 * guarda. Se usa {@link BigDecimal}, nunca {@code float} ni {@code double} (HU-04.02 crit. 4).
 */
public record TasaIva(BigDecimal valor) {

    /** Decimales de la tasa; coinciden con {@code numeric(5,4)}. */
    public static final int DECIMALES = 4;

    /** IVA del 12 % que se aplica a todas las ventas nuevas. */
    public static final TasaIva VIGENTE = new TasaIva(new BigDecimal("0.12"));

    public TasaIva {
        Objects.requireNonNull(valor, "La tasa de IVA es obligatoria.");
        if (valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("La tasa de IVA debe estar entre 0 y 1 (fracción): " + valor);
        }
        if (valor.stripTrailingZeros().scale() > DECIMALES) {
            throw new IllegalArgumentException(
                    "La tasa de IVA admite como máximo " + DECIMALES + " decimales: " + valor);
        }
        valor = valor.setScale(DECIMALES);
    }

    /** Reconstruye la tasa guardada en una venta (crit. 3). */
    public static TasaIva de(BigDecimal valorGuardado) {
        return new TasaIva(valorGuardado);
    }

    /** {@code 1 + tasa}, el divisor para obtener la base imponible desde el precio con IVA ({@code 1.1200}). */
    public BigDecimal divisor() {
        return BigDecimal.ONE.add(valor);
    }

    /** La tasa como porcentaje, por ejemplo {@code 12} para mostrarla en pantalla o en el ticket. */
    public BigDecimal porcentaje() {
        BigDecimal porcentaje = valor.movePointRight(2).stripTrailingZeros();
        // Sin notación exponencial: 10 % es "10", no "1E+1".
        return porcentaje.scale() < 0 ? porcentaje.setScale(0) : porcentaje;
    }
}
