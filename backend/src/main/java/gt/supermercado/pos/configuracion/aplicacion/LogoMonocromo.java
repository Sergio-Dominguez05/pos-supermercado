package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.LogoTicket;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * HU-12.09 crit. 1: convierte la imagen que sube el Administrador (PNG, JPEG, GIF o BMP) en el logo de un bit por punto
 * que imprime la impresora térmica: la escala para que quepa en {@link LogoTicket#ANCHO_MAXIMO} por
 * {@link LogoTicket#ALTO_MAXIMO} puntos sin deformarla, la deja en blanco y negro (lo transparente es blanco) y completa
 * el ancho a un múltiplo de 8. También la devuelve como PNG para la vista previa.
 */
public final class LogoMonocromo {

    /** Un logo más pesado no tiene sentido para un ticket y se rechaza antes de abrirlo. */
    public static final int BYTES_MAXIMOS = 1_000_000;
    private static final int UMBRAL = 128;

    private LogoMonocromo() {
    }

    /** La imagen en Base64, con o sin el prefijo {@code data:image/...;base64,}. */
    public static LogoTicket desdeBase64(String base64) {
        String datos = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(datos);
        } catch (IllegalArgumentException e) {
            throw invalido();
        }
        if (bytes.length > BYTES_MAXIMOS) {
            throw new NegocioException("LOGO_DEMASIADO_GRANDE", "El logo puede pesar como máximo 1 MB.");
        }
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            throw invalido();
        }
        if (imagen == null || imagen.getWidth() <= 0 || imagen.getHeight() <= 0) {
            throw invalido();
        }
        return monocromo(imagen);
    }

    static LogoTicket monocromo(BufferedImage original) {
        double escala = Math.min(1.0, Math.min((double) LogoTicket.ANCHO_MAXIMO / original.getWidth(),
                (double) LogoTicket.ALTO_MAXIMO / original.getHeight()));
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));
        int anchoBytes = (ancho + 7) / 8;
        BufferedImage lienzo = new BufferedImage(anchoBytes * 8, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, lienzo.getWidth(), alto);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, (lienzo.getWidth() - ancho) / 2, 0, ancho, alto, null);
        g.dispose();
        byte[] puntos = new byte[anchoBytes * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < lienzo.getWidth(); x++) {
                int rgb = lienzo.getRGB(x, y);
                int luz = (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF) + 114 * (rgb & 0xFF)) / 1000;
                if (luz < UMBRAL) {
                    puntos[y * anchoBytes + x / 8] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return new LogoTicket(anchoBytes * 8, alto, puntos);
    }

    /** El logo como PNG en blanco y negro, para mostrarlo tal como saldrá impreso. */
    public static byte[] png(LogoTicket logo) {
        BufferedImage imagen = new BufferedImage(logo.ancho(), logo.alto(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < logo.alto(); y++) {
            for (int x = 0; x < logo.ancho(); x++) {
                imagen.setRGB(x, y, logo.negro(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** El PNG como {@code data:image/png;base64,...}, listo para una etiqueta {@code <img>}. */
    public static String dataUrl(LogoTicket logo) {
        return logo == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(png(logo));
    }

    private static NegocioException invalido() {
        return new NegocioException("LOGO_INVALIDO", "El logo debe ser una imagen PNG, JPG, GIF o BMP.");
    }
}
