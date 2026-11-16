package gt.supermercado.pos.impuestos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-04.05 Aplicar reglas de redondeo. Las reglas están documentadas en {@code docs/arquitectura/calculo-iva.md}
 * (crit. 4).
 */
class RedondeoTest {

    // Crit. 3: al medio hacia arriba

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "10.715,    10.72",   // ejemplo de la historia
            "10.714,    10.71",   // ejemplo de la historia
            "10.7149999, 10.71",  // solo cuenta el primer decimal que se elimina
            "10.725,    10.73",   // al medio siempre hacia arriba, no al par (HALF_EVEN daría 10.72)
            "10.705,    10.71",
            "0.005,     0.01",
            "0.004999,  0.00",
            "99.995,    100.00",  // el acarreo sube a los enteros
            "12,        12.00",
            "1.607142,  1.61"})
    void redondeaLosTotalesADosDecimalesAlMedioHaciaArriba(String monto, String esperado) {
        assertThat(CalculadoraIva.redondearTotal(new BigDecimal(monto))).isEqualTo(new BigDecimal(esperado));
    }

    // Crit. 1: los valores por línea y las sumas intermedias conservan 6 decimales

    @Test
    void losValoresPorLineaNoSeRedondeanADosDecimales() {
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal("5.00"), 1);

        assertThat(linea.base()).isEqualTo(new BigDecimal("4.464286"));
        assertThat(linea.iva()).isEqualTo(new BigDecimal("0.535714"));
    }

    @Test
    void lasSumasIntermediasConservanSeisDecimalesYSoloElResultadoSeRedondea() {
        // IVA de 7 líneas de Q5.00: 7 × 0.535714 = 3.749998 → 3.75.
        // Redondeando cada línea primero: 7 × 0.54 = 3.78, tres centavos de más.
        List<LineaCalculada> lineas = java.util.Collections.nCopies(7, CalculadoraIva.linea(new BigDecimal("5.00"), 1));

        Totales totales = CalculadoraIva.totales(lineas);

        assertThat(totales.iva()).isEqualTo(new BigDecimal("3.75"));
        assertThat(totales.total()).isEqualTo(new BigDecimal("35.00"));
        assertThat(totales.subtotal()).isEqualTo(new BigDecimal("31.25"));
    }

    // Crit. 2: solo Subtotal, IVA y TOTAL tienen 2 decimales

    @Test
    void soloLosResultadosFinalesTienenDosDecimales() {
        Totales totales = CalculadoraIva.totales(List.of(CalculadoraIva.linea(new BigDecimal("3.33"), 3)));

        assertThat(List.of(totales.subtotal(), totales.iva(), totales.total()))
                .allSatisfy(monto -> assertThat(monto.scale()).isEqualTo(CalculadoraIva.DECIMALES_TOTALES));
    }

    @Test
    void laBaseImponibleTambienSeRedondeaAlMedioHaciaArribaEnSuSextoDecimal() {
        // 1.00 / 1.12 = 0.892857|14... → 0.892857 (el séptimo decimal es 1: se queda).
        assertThat(CalculadoraIva.desglosar(new BigDecimal("1.00")).base()).isEqualTo(new BigDecimal("0.892857"));
        // 0.15 / 1.12 = 0.133928|57... → 0.133929 (el séptimo decimal es 5: sube).
        assertThat(CalculadoraIva.desglosar(new BigDecimal("0.15")).base()).isEqualTo(new BigDecimal("0.133929"));
    }

    @Test
    void unMontoFaltanteSeRechaza() {
        assertThatThrownBy(() -> CalculadoraIva.redondearTotal(null)).isInstanceOf(NullPointerException.class);
    }
}
