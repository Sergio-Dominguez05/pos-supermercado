package gt.supermercado.pos.facturacion.dominio;

import java.util.UUID;

/**
 * HU-08.15: una factura por enviar por correo, con lo que lleva el mensaje.
 *
 * @param xml      el DTE certificado si se guardó; si no, el que se envió al certificador
 * @param intentos los envíos que ya fallaron
 */
public record EnvioPendiente(long facturaId, long ventaId, long ventaNumero, EstadoFactura estado, String correo,
                             String serie, String numero, UUID autorizacion, String xml, int intentos) {
}
