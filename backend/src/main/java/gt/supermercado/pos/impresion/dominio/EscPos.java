package gt.supermercado.pos.impresion.dominio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Convierte un {@link Ticket} en comandos ESC/POS, el lenguaje de las impresoras térmicas (Architecture.md §3). El
 * texto va en la página de códigos 850, que tiene las tildes, la ñ, "º" y "·" del español.
 */
public final class EscPos {

    private static final Charset PAGINA_850 = Charset.forName("IBM850");
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final int AVANCE_ANTES_DEL_CORTE = 4;

    private EscPos() {
    }

    /** Los bytes del ticket, repetido {@code copias} veces, cada copia con su corte de papel (HU-12.08 crit. 3). */
    public static byte[] codificar(Ticket ticket, int copias) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (int copia = 0; copia < copias; copia++) {
            salida.writeBytes(new byte[] {ESC, '@'});                // reinicia la impresora
            salida.writeBytes(new byte[] {ESC, 't', 2});             // página de códigos 850
            for (Ticket.Renglon renglon : ticket.renglones()) {
                salida.writeBytes(new byte[] {ESC, 'a', (byte) (renglon.alineacion() == Ticket.Alineacion.CENTRO ? 1 : 0)});
                salida.writeBytes(new byte[] {ESC, 'E', (byte) (renglon.negrita() ? 1 : 0)});
                salida.writeBytes(new byte[] {GS, '!', (byte) (renglon.destacado() ? 0x01 : 0x00)});   // doble alto
                salida.writeBytes(renglon.texto().getBytes(PAGINA_850));
                salida.write('\n');
            }
            salida.writeBytes(new byte[] {ESC, 'd', AVANCE_ANTES_DEL_CORTE});
            salida.writeBytes(new byte[] {GS, 'V', 66, 0});          // avanza y corta el papel
        }
        return salida.toByteArray();
    }
}
