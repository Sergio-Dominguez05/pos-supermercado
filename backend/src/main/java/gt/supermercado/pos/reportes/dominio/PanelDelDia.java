package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.12 Panel resumen del día.
 * <ul>
 *   <li>Crit. 1: el total vendido hoy, la cantidad de ventas y el ticket promedio (ventas finalizadas hoy, en hora de
 *       Guatemala, sin las de facturas anuladas, como HU-10.02), las cajas abiertas y los pagos de hoy por tipo de
 *       tarjeta.</li>
 *   <li>Crit. 2: las alertas: comprobantes en contingencia pendientes de certificar (de cualquier día, porque siguen
 *       pendientes), códigos no encontrados en caja hoy y sin resolver (HU-03.08) y cierres de hoy con diferencia.</li>
 *   <li>Crit. 3: la pantalla lo vuelve a pedir periódicamente; {@code actualizadoEn} dice de cuándo son los datos.</li>
 * </ul>
 */
public record PanelDelDia(LocalDate fecha, OffsetDateTime actualizadoEn, long cantidadVentas, BigDecimal totalVendido,
                          BigDecimal ticketPromedio, List<CajaAbierta> cajasAbiertas,
                          List<VentasConTarjeta> ventasPorTarjeta, Alertas alertas) {

    /** Una caja con su turno abierto: quién la atiende y desde cuándo. */
    public record CajaAbierta(long turnoId, String caja, String cajero, OffsetDateTime abiertaDesde) {}

    /** Los pagos de hoy con un tipo de tarjeta (sin los de facturas anuladas). */
    public record VentasConTarjeta(TipoTarjetaReporte tipo, String etiqueta, long transacciones, BigDecimal monto) {}

    /** Crit. 2: lo que requiere atención; todo en cero es que no hay nada pendiente. */
    public record Alertas(long contingenciaPendientes, long codigosNoEncontrados, long cierresConDiferencia) {

        public long total() {
            return contingenciaPendientes + codigosNoEncontrados + cierresConDiferencia;
        }
    }

    /** Las ventas de hoy, tal como las trae la consulta. */
    public record VentasDeHoy(long cantidad, BigDecimal total) {}

    public static PanelDelDia de(LocalDate fecha, OffsetDateTime actualizadoEn, VentasDeHoy ventas,
                                 List<CajaAbierta> cajas, List<ReporteTarjetas.PagosDelTipo> pagos, Alertas alertas) {
        List<VentasConTarjeta> tarjetas = java.util.Arrays.stream(TipoTarjetaReporte.values()).map(tipo -> {
            List<ReporteTarjetas.PagosDelTipo> delTipo = pagos.stream()
                    .filter(pago -> pago.tipo() == tipo && !pago.anulada()).toList();
            return new VentasConTarjeta(tipo, tipo.etiqueta(),
                    delTipo.stream().mapToLong(ReporteTarjetas.PagosDelTipo::transacciones).sum(),
                    delTipo.stream().map(ReporteTarjetas.PagosDelTipo::monto).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP));
        }).toList();
        return new PanelDelDia(fecha, actualizadoEn, ventas.cantidad(), ventas.total().setScale(2, RoundingMode.HALF_UP),
                ReporteVentasPeriodo.promedio(ventas.total(), ventas.cantidad()), List.copyOf(cajas), tarjetas, alertas);
    }
}
