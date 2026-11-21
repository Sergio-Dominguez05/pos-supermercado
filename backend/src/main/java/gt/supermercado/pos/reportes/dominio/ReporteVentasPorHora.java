package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-10.11 Reporte de ventas por hora.
 * <ul>
 *   <li>Crit. 1: por cada hora del día (de 00:00 a 23:00, en hora de Guatemala), la cantidad de ventas y el total
 *       vendido. Aparecen las 24 horas, también las que no tuvieron ventas. Como en HU-10.02, son las ventas
 *       finalizadas sin las de facturas anuladas.</li>
 *   <li>Crit. 2: con un solo día, son las ventas de ese día; con un período, además del total se muestra el promedio
 *       por día de cada hora (total entre la cantidad de días del período, también los días sin ventas).</li>
 *   <li>Crit. 3: la gráfica de barras la dibuja la pantalla con estos datos; {@code horaPico} es la hora con más
 *       ventas, para resaltarla.</li>
 * </ul>
 */
public record ReporteVentasPorHora(EncabezadoReporte encabezado, long dias, boolean promedio, List<Fila> filas,
                                   Fila totales, Integer horaPico) {

    public static final String TITULO = "Ventas por hora";

    /**
     * Una hora del día, o la fila de totales ({@code hora} nula).
     *
     * @param promedioVentas ventas por día en esa hora, con un decimal
     * @param promedioTotal  monto por día en esa hora, en centavos
     */
    public record Fila(Integer hora, String etiqueta, long cantidadVentas, BigDecimal totalVendido,
                       BigDecimal promedioVentas, BigDecimal promedioTotal) {}

    /** Lo que trae la consulta: las horas con ventas. */
    public record VentasDeLaHora(int hora, long cantidad, BigDecimal total) {}

    public static ReporteVentasPorHora de(EncabezadoReporte encabezado, FiltrosReporte filtros,
                                          List<VentasDeLaHora> horas) {
        long dias = ChronoUnit.DAYS.between(filtros.desde(), filtros.hasta()) + 1;
        Map<Integer, VentasDeLaHora> porHora = horas.stream()
                .collect(Collectors.toMap(VentasDeLaHora::hora, Function.identity()));
        List<Fila> filas = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            VentasDeLaHora ventas = porHora.getOrDefault(hora, new VentasDeLaHora(hora, 0, BigDecimal.ZERO));
            filas.add(fila(hora, etiqueta(hora), ventas.cantidad(), ventas.total(), dias));
        }
        long cantidad = filas.stream().mapToLong(Fila::cantidadVentas).sum();
        BigDecimal total = filas.stream().map(Fila::totalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        Integer pico = cantidad == 0 ? null : filas.stream()
                .max(Comparator.comparingLong(Fila::cantidadVentas).thenComparing(Fila::totalVendido)
                        .thenComparing(Fila::hora, Comparator.reverseOrder()))
                .map(Fila::hora).orElse(null);
        return new ReporteVentasPorHora(encabezado, dias, dias > 1, List.copyOf(filas),
                fila(null, "Total", cantidad, total, dias), pico);
    }

    /** "08:00 – 08:59": la hora con sus minutos, para que se lea sin ambigüedad. */
    static String etiqueta(int hora) {
        return "%02d:00 – %02d:59".formatted(hora, hora);
    }

    private static Fila fila(Integer hora, String etiqueta, long cantidad, BigDecimal total, long dias) {
        BigDecimal dineroTotal = total.setScale(2, RoundingMode.HALF_UP);
        return new Fila(hora, etiqueta, cantidad, dineroTotal,
                BigDecimal.valueOf(cantidad).divide(BigDecimal.valueOf(dias), 1, RoundingMode.HALF_UP),
                total.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP));
    }
}
