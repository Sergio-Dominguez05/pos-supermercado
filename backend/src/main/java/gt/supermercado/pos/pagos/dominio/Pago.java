package gt.supermercado.pos.pagos.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Un pago con tarjeta procesado en la terminal bancaria (HU-07.03). Nunca guarda el número completo de la tarjeta:
 * solo, si el cajero los registra, los últimos 4 dígitos (crit. 4).
 *
 * @param ultimos4 los últimos 4 dígitos, o {@code null} si no se registraron
 * @param monto    en quetzales (crit. 7)
 */
public record Pago(
        long id,
        long ventaId,
        long turnoId,
        long usuarioId,
        TipoTarjeta tipoTarjeta,
        String autorizacion,
        String ultimos4,
        BigDecimal monto,
        OffsetDateTime registradoEn) {
}
