package gt.supermercado.pos.facturacion.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * HU-08.08 y HU-08.09: una factura que todavía no se certifica (en contingencia o pendiente), con su tarea de
 * certificación: cuántas veces se intentó, el error del último intento y cuándo se vuelve a intentar.
 */
public record FacturaPendiente(long facturaId, long ventaId, long ventaNumero, String cajaNombre, EstadoFactura estado,
                               Long numeroAcceso, OffsetDateTime emitidaEn, BigDecimal total, int intentos,
                               String ultimoError, OffsetDateTime proximoIntento) {
}
