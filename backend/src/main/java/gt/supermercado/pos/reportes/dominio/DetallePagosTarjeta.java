package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.06 crit. 3: las transacciones de un tipo de tarjeta, con su número de autorización, para buscarlas en el
 * estado de cuenta del banco. Incluye las de facturas anuladas, marcadas.
 */
public record DetallePagosTarjeta(EncabezadoReporte encabezado, List<Pago> pagos) {

    public static final String TITULO = "Detalle de transacciones con tarjeta";

    /** @param ultimos4 los últimos cuatro dígitos, si se registraron; nunca el número completo */
    public record Pago(long pagoId, OffsetDateTime fecha, long ventaNumero, String caja, String cajero,
                       String autorizacion, String ultimos4, BigDecimal monto, boolean anulada) {}
}
