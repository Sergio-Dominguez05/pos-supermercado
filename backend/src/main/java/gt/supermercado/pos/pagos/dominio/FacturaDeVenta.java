package gt.supermercado.pos.pagos.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cómo quedó la factura de una venta al finalizar el cobro (HU-08.03): lo que la pantalla le muestra al cajero.
 *
 * @param estado      {@code CERTIFICADA}, {@code CONTINGENCIA}, {@code RECHAZADA} o {@code PENDIENTE}
 * @param autorizacion el número de autorización (UUID) de la SAT, si se certificó
 * @param motivo      por qué no se certificó, si no se certificó, en lenguaje comprensible (HU-08.06 crit. 1)
 * @param corregible  HU-08.06 crit. 2: el rechazo se corrige en caja (el cliente) y se reenvía sin volver a cobrar
 */
public record FacturaDeVenta(String estado, UUID autorizacion, String serie, String numero,
                             OffsetDateTime certificadaEn, String motivo, boolean corregible) {
}
