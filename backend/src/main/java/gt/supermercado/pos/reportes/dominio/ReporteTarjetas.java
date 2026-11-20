package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * HU-10.06 Reporte de ingresos por tipo de tarjeta, para conciliar con los depósitos del banco.
 * <ul>
 *   <li>Crit. 1: por tipo de tarjeta (débito y crédito), la cantidad de transacciones y el total recibido. Cuentan
 *       los pagos vigentes (no eliminados) de las ventas finalizadas en el período.</li>
 *   <li>Crit. 2: los pagos de ventas con factura anulada van aparte y no se suman al total.</li>
 * </ul>
 * Siempre aparecen las dos filas (o solo la del tipo filtrado), también en cero.
 */
public record ReporteTarjetas(EncabezadoReporte encabezado, List<Fila> filas, Fila total, List<Fila> anulados,
                              Fila totalAnulado) {

    public static final String TITULO = "Ingresos por tipo de tarjeta";

    /** Un tipo de tarjeta, o la fila de totales ({@code tipo} nulo). */
    public record Fila(TipoTarjetaReporte tipo, String etiqueta, long transacciones, BigDecimal monto) {}

    /** Los pagos de un tipo de tarjeta, separados en vigentes y de facturas anuladas. */
    public record PagosDelTipo(TipoTarjetaReporte tipo, boolean anulada, long transacciones, BigDecimal monto) {}

    public static ReporteTarjetas de(EncabezadoReporte encabezado, TipoTarjetaReporte filtro, List<PagosDelTipo> pagos) {
        List<TipoTarjetaReporte> tipos = filtro == null ? Arrays.asList(TipoTarjetaReporte.values()) : List.of(filtro);
        List<Fila> filas = tipos.stream().map(tipo -> fila(tipo, pagos, false)).toList();
        List<Fila> anulados = tipos.stream().map(tipo -> fila(tipo, pagos, true)).toList();
        return new ReporteTarjetas(encabezado, filas, total(filas), anulados, total(anulados));
    }

    private static Fila fila(TipoTarjetaReporte tipo, List<PagosDelTipo> pagos, boolean anulada) {
        List<PagosDelTipo> delTipo = pagos.stream().filter(p -> p.tipo() == tipo && p.anulada() == anulada).toList();
        return new Fila(tipo, tipo.etiqueta(), delTipo.stream().mapToLong(PagosDelTipo::transacciones).sum(),
                dinero(delTipo.stream().map(PagosDelTipo::monto).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private static Fila total(List<Fila> filas) {
        return new Fila(null, "Total", filas.stream().mapToLong(Fila::transacciones).sum(),
                dinero(filas.stream().map(Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
