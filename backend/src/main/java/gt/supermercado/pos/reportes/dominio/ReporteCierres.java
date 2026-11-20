package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.08 Reporte de cierres de caja, para controlar faltantes y sobrantes.
 * <ul>
 *   <li>Crit. 1: por cierre, la fecha, la caja, el cajero, el total registrado con tarjeta, el total del cierre de
 *       lote, la diferencia y su justificación (HU-09.09 crit. 2). Cuentan los turnos cerrados en el período.</li>
 *   <li>Crit. 2: el total de faltantes y el de sobrantes. La diferencia es lote − registrado (HU-09.08): negativa es
 *       un faltante y positiva un sobrante. Los faltantes se informan como monto positivo.</li>
 *   <li>Crit. 3: con {@code soloConDiferencias}, solo los cierres cuya diferencia no es cero.</li>
 * </ul>
 */
public record ReporteCierres(EncabezadoReporte encabezado, List<Cierre> cierres, long cierresConDiferencia,
                             BigDecimal totalFaltantes, BigDecimal totalSobrantes) {

    public static final String TITULO = "Cierres de caja";

    public record Cierre(long turnoId, OffsetDateTime abiertoEn, OffsetDateTime cerradoEn, String caja, String cajero,
                         String cerradoPor, BigDecimal totalRegistrado, BigDecimal totalLote, BigDecimal diferencia,
                         String justificacion) {}

    public static ReporteCierres de(EncabezadoReporte encabezado, List<Cierre> cierres) {
        BigDecimal faltantes = cierres.stream().map(Cierre::diferencia).filter(d -> d.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate();
        BigDecimal sobrantes = cierres.stream().map(Cierre::diferencia).filter(d -> d.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReporteCierres(encabezado, List.copyOf(cierres),
                cierres.stream().filter(c -> c.diferencia().signum() != 0).count(), dinero(faltantes),
                dinero(sobrantes));
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
