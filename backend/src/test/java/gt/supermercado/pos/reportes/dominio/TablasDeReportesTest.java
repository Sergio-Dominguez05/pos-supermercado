package gt.supermercado.pos.reportes.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-10.13 crit. 2 y 3, HU-10.14 crit. 2: lo que se exporta es lo mismo que se ve, con sus tipos. */
class TablasDeReportesTest {

    private static final EncabezadoReporte ENCABEZADO = new EncabezadoReporte("Prueba", List.of(),
            OffsetDateTime.parse("2026-11-20T10:00:00-06:00"), "Ana (ana)");

    @Test
    void lasVentasPorPeriodoSeExportanConLasMismasFilasYLaFilaDeTotales() {
        ReporteVentasPeriodo reporte = ReporteVentasPeriodo.de(ENCABEZADO,
                FiltrosReporte.delPeriodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), Agrupacion.DIA,
                List.of(new VentasDelDia(LocalDate.of(2026, 11, 1), 2, new BigDecimal("20.00"), new BigDecimal("2.40"),
                        new BigDecimal("22.40"))));

        TablaReporte tabla = TablasDeReportes.de(CodigoReporte.VENTAS_PERIODO, reporte);

        assertThat(tabla.columnas()).extracting(TablaReporte.Columna::titulo).containsExactly("Período", "Ventas",
                "Subtotal", "IVA", "Total vendido", "Ticket promedio");
        assertThat(tabla.columnas()).extracting(TablaReporte.Columna::tipo).containsExactly(TablaReporte.Tipo.TEXTO,
                TablaReporte.Tipo.ENTERO, TablaReporte.Tipo.DINERO, TablaReporte.Tipo.DINERO, TablaReporte.Tipo.DINERO,
                TablaReporte.Tipo.DINERO);
        assertThat(tabla.filas()).extracting(fila -> fila.getFirst()).containsExactly("01/11/2026", "02/11/2026");
        assertThat(tabla.filas().getFirst()).containsExactly("01/11/2026", 2L, new BigDecimal("20.00"),
                new BigDecimal("2.40"), new BigDecimal("22.40"), new BigDecimal("11.20"));
        assertThat(tabla.pie()).hasSize(1);
        assertThat(tabla.pie().getFirst().getFirst()).isEqualTo("Total");
        assertThat(tabla.notas()).containsExactly("Las facturas anuladas no se suman a las ventas.");
    }

    @Test
    void lasVentasPorCajaExportanLaParticipacionComoNumero() {   // HU-10.04, HU-10.14 crit. 2
        ReporteVentasPorCaja reporte = ReporteVentasPorCaja.de(ENCABEZADO, List.of(
                new ReporteVentasPorCaja.VentasDeCaja(1, "Caja 1", 3, new BigDecimal("30.00"), 2)));

        TablaReporte tabla = TablasDeReportes.de(CodigoReporte.VENTAS_CAJA, reporte);

        assertThat(tabla.columnas()).extracting(TablaReporte.Columna::titulo).containsExactly("Caja", "Ventas",
                "Total vendido", "Turnos", "Ticket promedio", "Participación");
        assertThat(tabla.columnas().getLast().tipo()).isEqualTo(TablaReporte.Tipo.PORCENTAJE);
        assertThat(tabla.filas().getFirst()).containsExactly("Caja 1", 3L, new BigDecimal("30.00"), 2L,
                new BigDecimal("10.00"), new BigDecimal("100.0"));
        assertThat(tabla.pie().getFirst().getFirst()).isEqualTo("Total");
    }

    @Test
    void lasTarjetasSeparanCantidadYMontoAnuladosEnDosColumnasNumericas() {
        ReporteTarjetas reporte = ReporteTarjetas.de(ENCABEZADO, null, List.of(
                new ReporteTarjetas.PagosDelTipo(TipoTarjetaReporte.DEBITO, true, 1, new BigDecimal("25.00"))));

        TablaReporte tabla = TablasDeReportes.de(CodigoReporte.TARJETAS, reporte);

        assertThat(tabla.filas().getFirst()).containsExactly("Débito", 0L, new BigDecimal("0.00"), 1L,
                new BigDecimal("25.00"));
        assertThat(tabla.pie().getFirst()).containsExactly("Total", 0L, new BigDecimal("0.00"), 1L,
                new BigDecimal("25.00"));
    }

    @Test
    void elIvaLlevaElAvisoDeFacturasSinCertificarEnElResumen() {
        ReporteIva reporte = ReporteIva.de(ENCABEZADO, List.of(), new ReporteIva.SinCertificar(2, new BigDecimal("41.20")));

        TablaReporte tabla = TablasDeReportes.de(CodigoReporte.IVA, reporte);

        assertThat(tabla.resumen()).extracting(TablaReporte.Dato::valor).containsExactly(2L, new BigDecimal("41.20"));
        assertThat(TablasDeReportes.de(CodigoReporte.IVA, ReporteIva.de(ENCABEZADO, List.of(),
                new ReporteIva.SinCertificar(0, BigDecimal.ZERO))).resumen()).isEmpty();
    }

    @Test
    void losDatosDeLaEmpresaOmitenLoQueFalta() {   // HU-10.13 crit. 2
        assertThat(new DatosEmpresa("Súper", "Súper, S.A.", "576937K", "Matriz", "Zona 1").lineas())
                .containsExactly("Súper", "Súper, S.A. · NIT 576937K", "Matriz · Zona 1");
        assertThat(new DatosEmpresa(null, null, null, "Matriz", null).lineas()).containsExactly("Matriz");
        assertThat(DatosEmpresa.SIN_CONFIGURAR.lineas()).containsExactly("Datos del emisor sin configurar");
    }
}
