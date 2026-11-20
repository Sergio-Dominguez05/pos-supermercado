package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * HU-10.04 Reporte de ventas por caja.
 * <ul>
 *   <li>Crit. 1: por caja, la cantidad de ventas y el total vendido (las finalizadas en el período, sin las de
 *       facturas anuladas, igual que HU-10.02) y la cantidad de turnos abiertos en el período.</li>
 *   <li>Crit. 2: todas las cajas en el mismo período, una fila cada una, con su participación en el total vendido y
 *       ordenadas de la que más vendió a la que menos; así se comparan de un vistazo. Aparecen las cajas activas,
 *       aunque no hayan vendido, y las inactivas que tuvieron ventas o turnos en el período.</li>
 * </ul>
 */
public record ReporteVentasPorCaja(EncabezadoReporte encabezado, List<Fila> filas, Fila totales) {

    public static final String TITULO = "Ventas por caja";

    /**
     * Una caja, o la fila de totales ({@code cajaId} nulo).
     *
     * @param participacion el porcentaje del total vendido que corresponde a la caja, con un decimal
     */
    public record Fila(Long cajaId, String caja, long cantidadVentas, BigDecimal totalVendido, long turnos,
                       BigDecimal ticketPromedio, BigDecimal participacion) {}

    /** Lo que trae la consulta de cada caja, antes de calcular el promedio y la participación. */
    public record VentasDeCaja(long cajaId, String caja, long cantidadVentas, BigDecimal totalVendido, long turnos) {}

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static ReporteVentasPorCaja de(EncabezadoReporte encabezado, List<VentasDeCaja> cajas) {
        long ventas = cajas.stream().mapToLong(VentasDeCaja::cantidadVentas).sum();
        BigDecimal total = cajas.stream().map(VentasDeCaja::totalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        long turnos = cajas.stream().mapToLong(VentasDeCaja::turnos).sum();
        List<Fila> filas = cajas.stream()
                .sorted(Comparator.comparing(VentasDeCaja::totalVendido).reversed()
                        .thenComparing(VentasDeCaja::cantidadVentas, Comparator.reverseOrder())
                        .thenComparing(caja -> caja.caja().toLowerCase()))
                .map(caja -> new Fila(caja.cajaId(), caja.caja(), caja.cantidadVentas(), dinero(caja.totalVendido()),
                        caja.turnos(), ReporteVentasPeriodo.promedio(caja.totalVendido(), caja.cantidadVentas()),
                        participacion(caja.totalVendido(), total)))
                .toList();
        Fila totales = new Fila(null, "Total", ventas, dinero(total), turnos,
                ReporteVentasPeriodo.promedio(total, ventas),
                total.signum() == 0 ? porcentaje(BigDecimal.ZERO) : porcentaje(CIEN));
        return new ReporteVentasPorCaja(encabezado, filas, totales);
    }

    /** El porcentaje del total, con un decimal (HALF_UP); 0 si en el período no se vendió nada. */
    static BigDecimal participacion(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? porcentaje(BigDecimal.ZERO)
                : parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal valor) {
        return valor.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
