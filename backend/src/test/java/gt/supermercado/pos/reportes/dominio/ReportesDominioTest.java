package gt.supermercado.pos.reportes.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-10.01 y HU-10.02: rangos rápidos, validación de filtros y agrupación por período. */
class ReportesDominioTest {

    private static final EncabezadoReporte ENCABEZADO = new EncabezadoReporte("Ventas por período", List.of(),
            OffsetDateTime.parse("2026-11-20T10:00:00-06:00"), "Prueba");

    @Test
    void losRangosRapidosSeCalculanDesdeLaFechaDeHoy() {   // HU-10.01 crit. 3
        LocalDate hoy = LocalDate.of(2026, 11, 20);   // viernes

        assertThat(RangoRapido.HOY.para(hoy)).isEqualTo(rango("2026-11-20", "2026-11-20"));
        assertThat(RangoRapido.AYER.para(hoy)).isEqualTo(rango("2026-11-19", "2026-11-19"));
        assertThat(RangoRapido.ESTA_SEMANA.para(hoy)).isEqualTo(rango("2026-11-16", "2026-11-20"));
        assertThat(RangoRapido.ESTE_MES.para(hoy)).isEqualTo(rango("2026-11-01", "2026-11-20"));
        assertThat(RangoRapido.MES_ANTERIOR.para(hoy)).isEqualTo(rango("2026-10-01", "2026-10-31"));
    }

    @Test
    void enLunesLaSemanaEsSoloHoyYEnEneroElMesAnteriorEsDiciembre() {
        assertThat(RangoRapido.ESTA_SEMANA.para(LocalDate.of(2026, 11, 16))).isEqualTo(rango("2026-11-16", "2026-11-16"));
        assertThat(RangoRapido.MES_ANTERIOR.para(LocalDate.of(2027, 1, 1))).isEqualTo(rango("2026-12-01", "2026-12-31"));
        assertThat(RangoRapido.MES_ANTERIOR.para(LocalDate.of(2028, 3, 31))).isEqualTo(rango("2028-02-01", "2028-02-29"));
    }

    @Test
    void lasFechasSonObligatoriasYNoPuedenEstarInvertidas() {   // HU-10.01 crit. 1
        assertThatThrownBy(() -> FiltrosReporte.delPeriodo(null, LocalDate.of(2026, 11, 1)))
                .isInstanceOf(NegocioException.class).hasMessage("Indique la fecha inicial y la final del reporte.");
        assertThatThrownBy(() -> FiltrosReporte.delPeriodo(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La fecha final no puede ser anterior a la fecha inicial.");
        assertThatThrownBy(() -> FiltrosReporte.delPeriodo(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(NegocioException.class).hasMessage("El reporte abarca como máximo cinco años.");
        assertThat(FiltrosReporte.delPeriodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)).desde())
                .isEqualTo("2026-11-01");
        assertThat(FiltrosReporte.delPeriodo(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31)).hasta())
                .isEqualTo("2024-12-31");   // cinco años justos
    }

    @Test
    void agrupaPorDiaConLosDiasSinVentasYLaFilaDeTotales() {   // HU-10.02 crit. 1 y 2
        ReporteVentasPeriodo reporte = ReporteVentasPeriodo.de(ENCABEZADO,
                FiltrosReporte.delPeriodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), Agrupacion.DIA,
                List.of(dia("2026-11-01", 2, "22.40"), dia("2026-11-03", 1, "10.00")));

        assertThat(reporte.filas()).extracting(ReporteVentasPeriodo.Fila::periodo)
                .containsExactly("01/11/2026", "02/11/2026", "03/11/2026");
        assertThat(reporte.filas().get(1).cantidadVentas()).isZero();
        assertThat(reporte.filas().get(1).ticketPromedio()).isEqualByComparingTo("0.00");
        assertThat(reporte.filas().get(0).ticketPromedio()).isEqualByComparingTo("11.20");
        assertThat(reporte.totales().periodo()).isEqualTo("Total");
        assertThat(reporte.totales().cantidadVentas()).isEqualTo(3);
        assertThat(reporte.totales().total()).isEqualByComparingTo("32.40");
        assertThat(reporte.totales().subtotal()).isEqualByComparingTo("28.93");
        assertThat(reporte.totales().iva()).isEqualByComparingTo("3.47");
        assertThat(reporte.totales().ticketPromedio()).isEqualByComparingTo("10.80");
    }

    @Test
    void agrupaPorSemanaDesdeElLunesYPorMesRecortandoAlRango() {   // HU-10.02 crit. 2
        FiltrosReporte filtros = FiltrosReporte.delPeriodo(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 10));
        List<VentasDelDia> dias = List.of(dia("2026-10-28", 1, "5.00"), dia("2026-11-02", 1, "7.00"));

        ReporteVentasPeriodo semanas = ReporteVentasPeriodo.de(ENCABEZADO, filtros, Agrupacion.SEMANA, dias);
        assertThat(semanas.filas()).extracting(ReporteVentasPeriodo.Fila::periodo).containsExactly(
                "Semana del 28/10/2026 al 01/11/2026", "Semana del 02/11/2026 al 08/11/2026",
                "Semana del 09/11/2026 al 10/11/2026");

        ReporteVentasPeriodo meses = ReporteVentasPeriodo.de(ENCABEZADO, filtros, Agrupacion.MES, dias);
        assertThat(meses.filas()).extracting(ReporteVentasPeriodo.Fila::periodo)
                .containsExactly("Octubre 2026", "Noviembre 2026");
        assertThat(meses.filas().get(0).desde()).isEqualTo("2026-10-28");
        assertThat(meses.filas().get(1).hasta()).isEqualTo("2026-11-10");
        assertThat(meses.filas()).extracting(f -> f.total().toPlainString()).containsExactly("5.00", "7.00");
    }

    @Test
    void elReportePorCajeroSumaCadaColumnaEnLaFilaDeTotales() {   // HU-10.03 crit. 1
        ReporteVentasPorCajero reporte = ReporteVentasPorCajero.de(ENCABEZADO, List.of(
                new ReporteVentasPorCajero.Fila(1L, "Ana (ana)", 3, new BigDecimal("30.50"), 1, 2, 0, BigDecimal.ZERO),
                new ReporteVentasPorCajero.Fila(2L, "Luis (luis)", 1, new BigDecimal("9.5"), 0, 4, 1,
                        new BigDecimal("12.00"))));

        ReporteVentasPorCajero.Fila totales = reporte.totales();
        assertThat(totales.cajeroId()).isNull();
        assertThat(totales.cajero()).isEqualTo("Total");
        assertThat(totales.cantidadVentas()).isEqualTo(4);
        assertThat(totales.totalVendido().toPlainString()).isEqualTo("40.00");
        assertThat(totales.ventasCanceladas()).isEqualTo(1);
        assertThat(totales.lineasEliminadas()).isEqualTo(6);
        assertThat(totales.facturasAnuladas()).isEqualTo(1);
        assertThat(totales.montoAnulado().toPlainString()).isEqualTo("12.00");
        assertThat(ReporteVentasPorCajero.de(ENCABEZADO, List.of()).totales().totalVendido().toPlainString())
                .isEqualTo("0.00");
    }

    @Test
    void lasTarjetasMuestranLosDosTiposEnCeroYLosAnuladosAparte() {   // HU-10.06 crit. 1 y 2
        ReporteTarjetas reporte = ReporteTarjetas.de(ENCABEZADO, null, List.of(
                new ReporteTarjetas.PagosDelTipo(TipoTarjetaReporte.CREDITO, false, 2, new BigDecimal("80.5")),
                new ReporteTarjetas.PagosDelTipo(TipoTarjetaReporte.CREDITO, true, 1, new BigDecimal("10.00"))));

        assertThat(reporte.filas()).extracting(ReporteTarjetas.Fila::etiqueta).containsExactly("Débito", "Crédito");
        assertThat(reporte.filas().get(0).transacciones()).isZero();
        assertThat(reporte.filas().get(0).monto().toPlainString()).isEqualTo("0.00");
        assertThat(reporte.total().monto().toPlainString()).isEqualTo("80.50");
        assertThat(reporte.totalAnulado().monto().toPlainString()).isEqualTo("10.00");
        assertThat(ReporteTarjetas.de(ENCABEZADO, TipoTarjetaReporte.DEBITO, List.of()).filas())
                .extracting(ReporteTarjetas.Fila::tipo).containsExactly(TipoTarjetaReporte.DEBITO);
    }

    @Test
    void elIvaSumaLosDiasEnElTotal() {   // HU-10.07 crit. 1
        ReporteIva reporte = ReporteIva.de(ENCABEZADO, List.of(
                new ReporteIva.Dia(LocalDate.of(2026, 11, 1), 2, new BigDecimal("150.00"), new BigDecimal("18.00"),
                        new BigDecimal("168.00")),
                new ReporteIva.Dia(LocalDate.of(2026, 11, 2), 1, new BigDecimal("20.00"), new BigDecimal("2.40"),
                        new BigDecimal("22.40"))), new ReporteIva.SinCertificar(0, BigDecimal.ZERO));

        assertThat(reporte.total().fecha()).isNull();
        assertThat(reporte.total().facturas()).isEqualTo(3);
        assertThat(reporte.total().baseImponible().toPlainString()).isEqualTo("170.00");
        assertThat(reporte.total().iva().toPlainString()).isEqualTo("20.40");
        assertThat(reporte.total().total().toPlainString()).isEqualTo("190.40");
    }

    @Test
    void losCierresSumanFaltantesYSobrantesPorSeparado() {   // HU-10.08 crit. 2
        ReporteCierres reporte = ReporteCierres.de(ENCABEZADO, List.of(cierre("0"), cierre("-9.5"), cierre("-0.50"),
                cierre("5")));

        assertThat(reporte.cierresConDiferencia()).isEqualTo(3);
        assertThat(reporte.totalFaltantes().toPlainString()).isEqualTo("10.00");
        assertThat(reporte.totalSobrantes().toPlainString()).isEqualTo("5.00");
        assertThat(ReporteCierres.de(ENCABEZADO, List.of()).totalFaltantes().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void lasAnuladasSumanSuMonto() {   // HU-10.09
        ReporteAnuladas reporte = ReporteAnuladas.de(ENCABEZADO, List.of(
                new ReporteAnuladas.Anulada(1, null, null, "A", "1", 1, "Caja 1", new BigDecimal("45"), "Error", "Ana",
                        "Luis"),
                new ReporteAnuladas.Anulada(2, null, null, "A", "2", 2, "Caja 1", new BigDecimal("30.5"), "Error",
                        "Ana", "Luis")));

        assertThat(reporte.cantidad()).isEqualTo(2);
        assertThat(reporte.monto().toPlainString()).isEqualTo("75.50");
    }

    private static ReporteCierres.Cierre cierre(String diferencia) {
        return new ReporteCierres.Cierre(1, null, null, "Caja 1", "Ana", "Ana", BigDecimal.TEN, BigDecimal.TEN,
                new BigDecimal(diferencia), null);
    }

    @Test
    void lasCajasSeOrdenanPorTotalConSuParticipacionYLaFilaDeTotales() {   // HU-10.04 crit. 1 y 2
        ReporteVentasPorCaja reporte = ReporteVentasPorCaja.de(ENCABEZADO, List.of(
                new ReporteVentasPorCaja.VentasDeCaja(1, "Caja 1", 1, new BigDecimal("10.00"), 1),
                new ReporteVentasPorCaja.VentasDeCaja(2, "Caja 2", 2, new BigDecimal("20.00"), 3),
                new ReporteVentasPorCaja.VentasDeCaja(3, "caja 0", 0, new BigDecimal("0.00"), 0),
                new ReporteVentasPorCaja.VentasDeCaja(4, "Caja 4", 0, new BigDecimal("0.00"), 1)));

        assertThat(reporte.filas()).extracting(ReporteVentasPorCaja.Fila::caja)
                .containsExactly("Caja 2", "Caja 1", "caja 0", "Caja 4");
        assertThat(reporte.filas()).extracting(f -> f.participacion().toPlainString())
                .containsExactly("66.7", "33.3", "0.0", "0.0");
        assertThat(reporte.filas().getFirst().ticketPromedio()).isEqualByComparingTo("10.00");
        ReporteVentasPorCaja.Fila totales = reporte.totales();
        assertThat(totales.cajaId()).isNull();
        assertThat(totales.cantidadVentas()).isEqualTo(3);
        assertThat(totales.totalVendido().toPlainString()).isEqualTo("30.00");
        assertThat(totales.turnos()).isEqualTo(5);
        assertThat(totales.participacion().toPlainString()).isEqualTo("100.0");
    }

    @Test
    void sinVentasLaParticipacionEsCero() {   // HU-10.04
        ReporteVentasPorCaja reporte = ReporteVentasPorCaja.de(ENCABEZADO, List.of(
                new ReporteVentasPorCaja.VentasDeCaja(1, "Caja 1", 0, BigDecimal.ZERO, 2)));

        assertThat(reporte.filas().getFirst().participacion().toPlainString()).isEqualTo("0.0");
        assertThat(reporte.totales().participacion().toPlainString()).isEqualTo("0.0");
        assertThat(reporte.totales().ticketPromedio()).isEqualByComparingTo("0.00");
    }

    @Test
    void elTicketPromedioSeRedondeaACentavos() {
        assertThat(ReporteVentasPeriodo.promedio(new BigDecimal("10.00"), 3)).isEqualByComparingTo("3.33");
        assertThat(ReporteVentasPeriodo.promedio(new BigDecimal("0.05"), 2)).isEqualByComparingTo("0.03");   // HALF_UP
        assertThat(ReporteVentasPeriodo.promedio(BigDecimal.ZERO, 0)).isEqualByComparingTo("0.00");
    }

    private static RangoRapido.RangoDeFechas rango(String desde, String hasta) {
        return new RangoRapido.RangoDeFechas(LocalDate.parse(desde), LocalDate.parse(hasta));
    }

    private static VentasDelDia dia(String fecha, long cantidad, String total) {
        BigDecimal monto = new BigDecimal(total);
        BigDecimal subtotal = monto.divide(new BigDecimal("1.12"), 2, java.math.RoundingMode.HALF_UP);
        return new VentasDelDia(LocalDate.parse(fecha), cantidad, subtotal, monto.subtract(subtotal), monto);
    }
}
