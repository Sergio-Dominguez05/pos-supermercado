package gt.supermercado.pos.impuestos.dominio;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-04.04 Calcular y mostrar los totales del comprobante. */
class TotalesTest {

    private static LineaCalculada linea(String precio, int cantidad) {
        return CalculadoraIva.linea(new BigDecimal(precio), cantidad);
    }

    @Test
    void unaVentaDeUnSoloProductoDeQ11_20() {   // crit. 2, 3 y 4
        Totales totales = CalculadoraIva.totales(List.of(linea("11.20", 1)));

        assertThat(totales.subtotal()).isEqualTo(new BigDecimal("10.00"));
        assertThat(totales.iva()).isEqualTo(new BigDecimal("1.20"));
        assertThat(totales.total()).isEqualTo(new BigDecimal("11.20"));
    }

    @Test
    void elIvaSeSumaConSeisDecimalesYSoloSeRedondeaAlFinal() {   // crit. 3
        // Tres líneas de Q5.00: IVA 0.535714 cada una. Sumadas: 1.607142 → 1.61.
        // Redondeando cada línea antes (0.54 × 3) daría 1.62.
        Totales totales = CalculadoraIva.totales(List.of(linea("5.00", 1), linea("5.00", 1), linea("5.00", 1)));

        assertThat(totales.iva()).isEqualTo(new BigDecimal("1.61"));
        assertThat(totales.total()).isEqualTo(new BigDecimal("15.00"));
        assertThat(totales.subtotal()).isEqualTo(new BigDecimal("13.39"));
    }

    @Test
    void elTotalEsLaSumaDeLosSubtotalesConIva() {   // crit. 4
        Totales totales = CalculadoraIva.totales(List.of(linea("11.20", 3), linea("5.00", 2), linea("0.99", 7)));

        assertThat(totales.total()).isEqualTo(new BigDecimal("50.53"));   // 33.60 + 10.00 + 6.93
    }

    @Test
    void elSubtotalEsLaSumaDeLasBasesRedondeada() {   // crit. 2
        List<LineaCalculada> lineas = List.of(linea("11.20", 3), linea("5.00", 2), linea("0.99", 7));
        BigDecimal sumaDeBases = lineas.stream().map(LineaCalculada::base).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(CalculadoraIva.totales(lineas).subtotal()).isEqualTo(sumaDeBases);
    }

    /** Crit. 5 con ventas al azar (semilla fija por repetición, para poder reproducir un fallo). */
    @RepeatedTest(50)
    void subtotalMasIvaEsSiempreIgualAlTotal(org.junit.jupiter.api.RepetitionInfo repeticion) {
        Random azar = new Random(repeticion.getCurrentRepetition());
        List<LineaCalculada> lineas = new ArrayList<>();
        for (int i = 0; i < 1 + azar.nextInt(200); i++) {
            BigDecimal precio = BigDecimal.valueOf(1 + azar.nextInt(50_000), 2);   // Q0.01 a Q500.00
            lineas.add(CalculadoraIva.linea(precio, 1 + azar.nextInt(50)));
        }

        Totales totales = CalculadoraIva.totales(lineas);

        assertThat(totales.subtotal().add(totales.iva())).isEqualTo(totales.total());
        assertThat(totales.subtotal().scale()).isEqualTo(2);
        assertThat(totales.iva().scale()).isEqualTo(2);
        assertThat(totales.total().scale()).isEqualTo(2);
    }

    @Test
    void sinLineasLosTotalesSonCero() {
        Totales totales = CalculadoraIva.totales(List.of());

        assertThat(totales).isEqualTo(new Totales(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
    }
}
