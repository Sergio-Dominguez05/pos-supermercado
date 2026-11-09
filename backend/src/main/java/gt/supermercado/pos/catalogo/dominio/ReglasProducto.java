package gt.supermercado.pos.catalogo.dominio;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Formato del código de barras, la descripción y el precio de un producto (HU-03.01 crit. 1, 4 y 5). */
public final class ReglasProducto {

    /** Largo máximo de la descripción; coincide con {@code producto.descripcion varchar(100)} (crit. 5). */
    public static final int LARGO_MAXIMO_DESCRIPCION = 100;

    /** Largo máximo del código de barras; cubre EAN-13, UPC, GTIN-14 y códigos internos. */
    public static final int LARGO_MAXIMO_CODIGO = 50;

    /** Decimales del precio; coinciden con {@code numeric(12,2)} (crit. 4). */
    public static final int DECIMALES_PRECIO = 2;

    /** El mayor precio que cabe en {@code numeric(12,2)}. */
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999999.99");

    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9-]{1," + LARGO_MAXIMO_CODIGO + "}");
    private static final Pattern ESPACIOS = Pattern.compile("[\\s\\p{Z}]+");

    private ReglasProducto() {
    }

    /**
     * Sin espacios y en mayúsculas. El lector de código de barras escribe el código como si fuera el teclado
     * (crit. 3), así que puede dejar espacios o un salto de línea al final.
     */
    public static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : ESPACIOS.matcher(codigo).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** Recibe el código ya normalizado: letras, números o guiones, de 1 a 50 caracteres. */
    public static boolean esCodigoValido(String codigo) {
        return CODIGO.matcher(codigo).matches();
    }

    /** Sin espacios al inicio ni al final y con uno solo entre palabras. */
    public static String normalizarDescripcion(String descripcion) {
        return descripcion == null ? "" : ESPACIOS.matcher(descripcion).replaceAll(" ").strip();
    }

    public static boolean esDescripcionValida(String descripcion) {
        return !descripcion.isEmpty() && descripcion.length() <= LARGO_MAXIMO_DESCRIPCION;
    }

    /** Mayor que cero, con 2 decimales como máximo ({@code 5.10} y {@code 5.1} sí; {@code 5.105} no). */
    public static boolean esPrecioValido(BigDecimal precio) {
        return precio != null
                && precio.signum() > 0
                && precio.stripTrailingZeros().scale() <= DECIMALES_PRECIO
                && precio.compareTo(PRECIO_MAXIMO) <= 0;
    }

    /** Recibe un precio válido y lo deja con exactamente 2 decimales. */
    public static BigDecimal normalizarPrecio(BigDecimal precio) {
        return precio.setScale(DECIMALES_PRECIO);
    }
}
