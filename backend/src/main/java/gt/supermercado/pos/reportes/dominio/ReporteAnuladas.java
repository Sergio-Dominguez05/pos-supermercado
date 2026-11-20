package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.09 Reporte de facturas anuladas, para controlar esta operación sensible.
 * <ul>
 *   <li>Crit. 1: la fecha de emisión y la de anulación, la serie y el número, el monto, el motivo, el cajero que
 *       emitió la factura y el usuario que la anuló.</li>
 *   <li>Crit. 2: se filtra por fecha de anulación (decisión de Sergio), por cajero (el que emitió) y por quien anuló.
 *       También por caja, como los demás reportes.</li>
 * </ul>
 */
public record ReporteAnuladas(EncabezadoReporte encabezado, List<Anulada> facturas, long cantidad, BigDecimal monto) {

    public static final String TITULO = "Facturas anuladas";

    public record Anulada(long facturaId, OffsetDateTime emitidaEn, OffsetDateTime anuladaEn, String serie,
                          String numero, long ventaNumero, String caja, BigDecimal monto, String motivo,
                          String cajero, String anuladaPor) {}

    public static ReporteAnuladas de(EncabezadoReporte encabezado, List<Anulada> facturas) {
        return new ReporteAnuladas(encabezado, List.copyOf(facturas), facturas.size(),
                facturas.stream().map(Anulada::monto).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP));
    }
}
