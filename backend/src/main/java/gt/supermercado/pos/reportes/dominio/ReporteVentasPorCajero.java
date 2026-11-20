package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * HU-10.03 Reporte de ventas por cajero (crit. 1). Por cajero, dentro del período:
 * <ul>
 *   <li>Ventas y total vendido: las finalizadas en el período (fecha de finalización), sin las de facturas anuladas,
 *       igual que HU-10.02.</li>
 *   <li>Ventas canceladas: por la fecha en que se iniciaron (la venta no guarda la hora de la cancelación; decisión
 *       de Sergio).</li>
 *   <li>Líneas eliminadas: por la fecha en que se eliminaron, de las ventas del cajero.</li>
 *   <li>Facturas anuladas: de las ventas finalizadas en el período, con su monto.</li>
 * </ul>
 * Solo aparecen los cajeros con alguna actividad en el período.
 */
public record ReporteVentasPorCajero(EncabezadoReporte encabezado, List<Fila> filas, Fila totales) {

    public static final String TITULO = "Ventas por cajero";

    public record Fila(Long cajeroId, String cajero, long cantidadVentas, BigDecimal totalVendido,
                       long ventasCanceladas, long lineasEliminadas, long facturasAnuladas, BigDecimal montoAnulado) {}

    public static ReporteVentasPorCajero de(EncabezadoReporte encabezado, List<Fila> filas) {
        Fila totales = new Fila(null, "Total",
                filas.stream().mapToLong(Fila::cantidadVentas).sum(),
                dinero(filas.stream().map(Fila::totalVendido).reduce(BigDecimal.ZERO, BigDecimal::add)),
                filas.stream().mapToLong(Fila::ventasCanceladas).sum(),
                filas.stream().mapToLong(Fila::lineasEliminadas).sum(),
                filas.stream().mapToLong(Fila::facturasAnuladas).sum(),
                dinero(filas.stream().map(Fila::montoAnulado).reduce(BigDecimal.ZERO, BigDecimal::add)));
        return new ReporteVentasPorCajero(encabezado, List.copyOf(filas), totales);
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
