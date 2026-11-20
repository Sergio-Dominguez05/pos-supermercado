package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HU-10.02 Reporte de ventas por período.
 * <ul>
 *   <li>Crit. 1: por período, la cantidad de ventas, el subtotal, el IVA, el total y el ticket promedio, con la fila
 *       de totales.</li>
 *   <li>Crit. 2: agrupado por día, semana o mes. Aparecen todos los períodos del rango, también los que no tuvieron
 *       ventas; el primero y el último se recortan al rango pedido.</li>
 *   <li>Crit. 3: las ventas de facturas anuladas no se suman (las excluye {@link RepositorioReportes}).</li>
 * </ul>
 */
public record ReporteVentasPeriodo(EncabezadoReporte encabezado, Agrupacion agrupacion, List<Fila> filas,
                                   Fila totales) {

    public static final String TITULO = "Ventas por período";

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("es", "GT"));

    /** Un período del reporte, o la fila de totales. */
    public record Fila(String periodo, LocalDate desde, LocalDate hasta, long cantidadVentas, BigDecimal subtotal,
                       BigDecimal iva, BigDecimal total, BigDecimal ticketPromedio) {}

    public static ReporteVentasPeriodo de(EncabezadoReporte encabezado, FiltrosReporte filtros, Agrupacion agrupacion,
                                          List<VentasDelDia> dias) {
        List<Fila> filas = new ArrayList<>();
        for (LocalDate inicio = agrupacion.inicioDe(filtros.desde()); !inicio.isAfter(filtros.hasta());
             inicio = agrupacion.siguiente(inicio)) {
            LocalDate desde = inicio.isBefore(filtros.desde()) ? filtros.desde() : inicio;
            LocalDate ultimo = agrupacion.siguiente(inicio).minusDays(1);
            LocalDate hasta = ultimo.isAfter(filtros.hasta()) ? filtros.hasta() : ultimo;
            filas.add(sumar(etiqueta(agrupacion, inicio, desde, hasta), desde, hasta, entre(dias, desde, hasta)));
        }
        return new ReporteVentasPeriodo(encabezado, agrupacion, List.copyOf(filas),
                sumar("Total", filtros.desde(), filtros.hasta(), entre(dias, filtros.desde(), filtros.hasta())));
    }

    private static List<VentasDelDia> entre(List<VentasDelDia> dias, LocalDate desde, LocalDate hasta) {
        return dias.stream().filter(dia -> !dia.fecha().isBefore(desde) && !dia.fecha().isAfter(hasta)).toList();
    }

    private static Fila sumar(String periodo, LocalDate desde, LocalDate hasta, List<VentasDelDia> dias) {
        long cantidad = dias.stream().mapToLong(VentasDelDia::cantidad).sum();
        BigDecimal subtotal = dias.stream().map(VentasDelDia::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal iva = dias.stream().map(VentasDelDia::iva).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = dias.stream().map(VentasDelDia::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Fila(periodo, desde, hasta, cantidad, dinero(subtotal), dinero(iva), dinero(total),
                promedio(total, cantidad));
    }

    /** El ticket promedio: total vendido entre la cantidad de ventas, redondeado a centavos (HALF_UP). */
    static BigDecimal promedio(BigDecimal total, long cantidad) {
        return cantidad == 0 ? dinero(BigDecimal.ZERO)
                : total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    private static String etiqueta(Agrupacion agrupacion, LocalDate inicio, LocalDate desde, LocalDate hasta) {
        return switch (agrupacion) {
            case DIA -> DIA.format(desde);
            case SEMANA -> "Semana del " + DIA.format(desde) + " al " + DIA.format(hasta);
            case MES -> {
                String mes = MES.format(inicio);
                yield Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
            }
        };
    }
}
