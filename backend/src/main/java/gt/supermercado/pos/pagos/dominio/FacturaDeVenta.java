package gt.supermercado.pos.pagos.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cómo quedó la factura de una venta al finalizar el cobro (HU-08.03): lo que la pantalla le muestra al cajero.
 *
 * @param estado      {@code CERTIFICADA}, {@code CONTINGENCIA}, {@code RECHAZADA} o {@code PENDIENTE}
 * @param autorizacion el número de autorización (UUID) de la SAT, si se certificó
 * @param motivo      por qué no se certificó, si no se certificó
 */
public record FacturaDeVenta(String estado, UUID autorizacion, String serie, String numero,
                             OffsetDateTime certificadaEn, String motivo) {
}
