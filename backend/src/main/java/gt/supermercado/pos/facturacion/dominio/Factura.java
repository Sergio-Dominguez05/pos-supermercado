package gt.supermercado.pos.facturacion.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * HU-08.03: la factura de una venta. Crit. 4: se asocia a la venta, que guarda la caja, el turno y el usuario.
 *
 * @param identificador el {@code venta.uuid}, que el certificador usa para no emitir dos facturas (HU-08.04)
 * @param xmlEnviado    el DTE tal como se envió al certificador (HU-08.02)
 */
public record Factura(long id, long ventaId, UUID identificador, EstadoFactura estado, UUID autorizacion, String serie,
                      String numero, OffsetDateTime fechaCertificacion, OffsetDateTime emitidaEn, String xmlEnviado) {
}
