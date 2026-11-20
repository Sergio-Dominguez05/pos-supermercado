package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-10.07 Reporte de IVA cobrado, para la declaración mensual ante la SAT.
 * <ul>
 *   <li>Crit. 1: la base imponible, el IVA y el total vendido.</li>
 *   <li>Crit. 2 y 4: solo las facturas certificadas, por su fecha de emisión (la del DTE), sin las anuladas, para que
 *       los totales coincidan con lo que tiene la SAT (decisión de Sergio). Los montos son los que la venta fijó y el
 *       DTE envió sin recalcular (HU-08.02).</li>
 *   <li>Crit. 3: el detalle por día (aquí) y por factura ({@link DetalleFacturasIva}).</li>
 * </ul>
 * Las ventas del período cuya factura aún no está certificada (pendiente, en contingencia o rechazada) se informan
 * aparte en {@code sinCertificar}, porque todavía no cuentan para la SAT.
 */
public record ReporteIva(EncabezadoReporte encabezado, List<Dia> dias, Dia total, SinCertificar sinCertificar) {

    public static final String TITULO = "IVA cobrado";

    /** Un día con facturas certificadas, o el total ({@code fecha} nula). */
    public record Dia(LocalDate fecha, long facturas, BigDecimal baseImponible, BigDecimal iva, BigDecimal total) {}

    /** Las facturas del período que todavía no están certificadas. */
    public record SinCertificar(long facturas, BigDecimal total) {}

    public static ReporteIva de(EncabezadoReporte encabezado, List<Dia> dias, SinCertificar sinCertificar) {
        Dia total = new Dia(null, dias.stream().mapToLong(Dia::facturas).sum(),
                suma(dias.stream().map(Dia::baseImponible).toList()), suma(dias.stream().map(Dia::iva).toList()),
                suma(dias.stream().map(Dia::total).toList()));
        return new ReporteIva(encabezado, List.copyOf(dias), total, sinCertificar);
    }

    private static BigDecimal suma(List<BigDecimal> montos) {
        return montos.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
