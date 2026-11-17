package gt.supermercado.pos.impresion.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * Un ticket para la impresora térmica, renglón por renglón, ya acomodado al ancho del papel (HU-12.08 crit. 1).
 * {@link EscPos} lo convierte en los bytes que entiende la impresora; {@link #texto()} lo muestra como texto plano.
 */
public final class Ticket {

    public enum Alineacion { IZQUIERDA, CENTRO }

    /** Un renglón impreso. {@code destacado} es negrita y doble alto, para el encabezado y el total. */
    public record Renglon(String texto, Alineacion alineacion, boolean negrita, boolean destacado) {}

    private final int columnas;
    private final List<Renglon> renglones = new ArrayList<>();

    private Ticket(int columnas) {
        this.columnas = columnas;
    }

    /** Un ticket vacío para papel de 58 mm (32 columnas) u 80 mm (48 columnas), con la letra normal. */
    public static Ticket paraPapel(int anchoMm) {
        return new Ticket(anchoMm == 58 ? 32 : 48);
    }

    public int columnas() {
        return columnas;
    }

    public List<Renglon> renglones() {
        return List.copyOf(renglones);
    }

    /** El texto, partido en renglones por palabras si no cabe. */
    public Ticket texto(String texto) {
        return agregar(texto, Alineacion.IZQUIERDA, false, false);
    }

    public Ticket centrado(String texto) {
        return agregar(texto, Alineacion.CENTRO, false, false);
    }

    public Ticket centradoNegrita(String texto) {
        return agregar(texto, Alineacion.CENTRO, true, false);
    }

    public Ticket titulo(String texto) {
        return agregar(texto, Alineacion.CENTRO, true, true);
    }

    /** Un dato a la izquierda y su valor a la derecha, como "IVA ....... Q1.61". */
    public Ticket dato(String izquierda, String derecha) {
        return dato(izquierda, derecha, false);
    }

    public Ticket datoNegrita(String izquierda, String derecha) {
        return dato(izquierda, derecha, true);
    }

    public Ticket separador() {
        renglones.add(new Renglon("-".repeat(columnas), Alineacion.IZQUIERDA, false, false));
        return this;
    }

    /** El ticket como texto plano, un renglón por línea. */
    public String texto() {
        StringBuilder texto = new StringBuilder();
        for (Renglon renglon : renglones) {
            texto.append(renglon.texto()).append('\n');
        }
        return texto.toString();
    }

    private Ticket dato(String izquierda, String derecha, boolean negrita) {
        int espacio = columnas - derecha.length();
        if (izquierda.length() + 1 > espacio) {
            agregar(izquierda, Alineacion.IZQUIERDA, negrita, false);
            renglones.add(new Renglon(" ".repeat(Math.max(0, espacio)) + derecha, Alineacion.IZQUIERDA, negrita, false));
            return this;
        }
        renglones.add(new Renglon(izquierda + " ".repeat(espacio - izquierda.length()) + derecha,
                Alineacion.IZQUIERDA, negrita, false));
        return this;
    }

    private Ticket agregar(String texto, Alineacion alineacion, boolean negrita, boolean destacado) {
        for (String renglon : partir(texto == null ? "" : texto.strip())) {
            renglones.add(new Renglon(renglon, alineacion, negrita, destacado));
        }
        return this;
    }

    /** Parte por palabras; una palabra más larga que el renglón se corta. */
    private List<String> partir(String texto) {
        List<String> resultado = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            while (palabra.length() > columnas) {
                if (!actual.isEmpty()) {
                    resultado.add(actual.toString());
                    actual.setLength(0);
                }
                resultado.add(palabra.substring(0, columnas));
                palabra = palabra.substring(columnas);
            }
            if (actual.isEmpty()) {
                actual.append(palabra);
            } else if (actual.length() + 1 + palabra.length() <= columnas) {
                actual.append(' ').append(palabra);
            } else {
                resultado.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
            }
        }
        if (!actual.isEmpty() || resultado.isEmpty()) {
            resultado.add(actual.toString());
        }
        return resultado;
    }
}
