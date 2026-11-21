package gt.supermercado.pos.configuracion.dominio;

import java.util.Arrays;

/**
 * HU-12.09 crit. 1: el logo del ticket en blanco y negro, como lo imprime una impresora térmica: una imagen de un bit
 * por punto, renglón por renglón, con 8 puntos por byte (el bit más alto es el punto de la izquierda; 1 es negro).
 *
 * @param ancho en puntos; múltiplo de 8 y como máximo {@value #ANCHO_MAXIMO} (cabe en papel de 58 mm)
 * @param alto  en puntos, como máximo {@value #ALTO_MAXIMO}
 */
public record LogoTicket(int ancho, int alto, byte[] puntos) {

    /** 384 puntos: el ancho que imprime el papel de 58 mm a 203 ppp; en el de 80 mm queda centrado. */
    public static final int ANCHO_MAXIMO = 384;
    public static final int ALTO_MAXIMO = 240;

    public LogoTicket {
        if (ancho <= 0 || ancho > ANCHO_MAXIMO || ancho % 8 != 0 || alto <= 0 || alto > ALTO_MAXIMO
                || puntos == null || puntos.length != ancho / 8 * alto) {
            throw new IllegalArgumentException("Logo de %dx%d con %d bytes".formatted(ancho, alto,
                    puntos == null ? 0 : puntos.length));
        }
        puntos = puntos.clone();
    }

    public int bytesPorRenglon() {
        return ancho / 8;
    }

    /** Si el punto (x, y) es negro. */
    public boolean negro(int x, int y) {
        return (puntos[y * bytesPorRenglon() + x / 8] & (0x80 >> (x % 8))) != 0;
    }

    @Override
    public byte[] puntos() {
        return puntos.clone();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof LogoTicket logo && logo.ancho == ancho && logo.alto == alto
                && Arrays.equals(logo.puntos, puntos);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ancho + alto) + Arrays.hashCode(puntos);
    }

    @Override
    public String toString() {
        return "LogoTicket[%dx%d]".formatted(ancho, alto);
    }
}
