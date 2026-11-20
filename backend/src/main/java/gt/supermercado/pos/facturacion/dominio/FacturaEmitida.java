package gt.supermercado.pos.facturacion.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * HU-08.10 crit. 1: una factura emitida, como se busca para anularla: con su venta, su caja y si el turno de la venta
 * sigue abierto (decisión D2).
 */
public record FacturaEmitida(long facturaId, long ventaId, long ventaNumero, long turnoId, boolean turnoAbierto,
                             String cajaNombre, EstadoFactura estado, String serie, String numero, UUID autorizacion,
                             OffsetDateTime fechaCertificacion, OffsetDateTime emitidaEn, BigDecimal total,
                             String receptorId, String receptorNombre, String motivoAnulacion,
                             OffsetDateTime anuladaEn) {
}
