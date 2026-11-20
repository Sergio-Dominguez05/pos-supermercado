package gt.supermercado.pos.facturacion.dominio;

import java.util.List;

/** HU-08.15: un correo listo para enviar, con sus adjuntos (el PDF y el XML de la factura). */
public record MensajeCorreo(String destinatario, String asunto, String texto, List<Adjunto> adjuntos) {

    public MensajeCorreo {
        adjuntos = List.copyOf(adjuntos);
    }

    /** Un archivo adjunto: su nombre, su tipo MIME y su contenido. */
    public record Adjunto(String nombre, String tipo, byte[] contenido) {}
}
