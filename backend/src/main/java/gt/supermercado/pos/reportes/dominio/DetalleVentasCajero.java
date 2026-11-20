package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.03 crit. 2: las ventas finalizadas y canceladas de un cajero en el período, en orden cronológico, con las
 * mismas fechas que el resumen (finalización o inicio).
 */
public record DetalleVentasCajero(EncabezadoReporte encabezado, List<Venta> ventas) {

    public static final String TITULO = "Detalle de ventas del cajero";

    /**
     * @param total          {@code null} en una venta cancelada: no llegó a fijar sus totales
     * @param estadoFactura  {@code null} si la venta no tiene factura
     * @param factura        serie y número de la factura certificada, o {@code null}
     */
    public record Venta(long ventaId, long numero, OffsetDateTime fecha, String caja, String estado, BigDecimal total,
                        String estadoFactura, String factura, long lineasEliminadas) {}
}
