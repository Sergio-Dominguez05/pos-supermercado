package gt.supermercado.pos.impuestos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04.03 Calcular el IVA por línea. */
class LineaCalculadaTest {

    @Test
    void laLineaTieneCantidadPrecioSubtotalBaseEIva() {   // crit. 1
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal("11.20"), 3);

        assertThat(linea.cantidad()).isEqualTo(3);
        assertThat(linea.precioUnitario()).isEqualTo(new BigDecimal("11.20"));
        assertThat(linea.subtotalConIva()).isEqualTo(new BigDecimal("33.600000"));
        assertThat(linea.base()).isEqualTo(new BigDecimal("30.000000"));
        assertThat(linea.iva()).isEqualTo(new BigDecimal("3.600000"));
    }

    @Test
    void laBaseYElIvaSeGuardanConSeisDecimalesSinRedondearADos() {   // crit. 2
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal("5.00"), 1);

        assertThat(linea.base()).isEqualTo(new BigDecimal("4.464286"));
        assertThat(linea.iva()).isEqualTo(new BigDecimal("0.535714"));
        assertThat(linea.subtotalConIva().scale()).isEqualTo(CalculadoraIva.DECIMALES);
    }

    @Test
    void elDesgloseSeHaceSobreElSubtotalYNoPorUnidad() {   // crit. 2: no acumula redondeos
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal("5.00"), 3);

        // 15.00 / 1.12 = 13.392857142...; por unidad daría 3 × 4.464286 = 13.392858.
        assertThat(linea.base()).isEqualTo(new BigDecimal("13.392857"));
        assertThat(linea.iva()).isEqualTo(new BigDecimal("1.607143"));
    }

    @ParameterizedTest
    @CsvSource({"0.01,1", "0.99,7", "3.33,3", "7.77,13", "19.99,250", "123.45,999", "9999.99,10000"})
    void laBaseMasElIvaSiempreSumanElSubtotalExacto(String precio, int cantidad) {
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal(precio), cantidad);

        assertThat(linea.subtotalConIva())
                .isEqualByComparingTo(new BigDecimal(precio).multiply(BigDecimal.valueOf(cantidad)));
        assertThat(linea.base().add(linea.iva())).isEqualTo(linea.subtotalConIva());
    }

    @Test
    void elPrecioUnitarioDelCatalogoNoSeModifica() {
        BigDecimal precioDelCatalogo = new BigDecimal("5.00");

        LineaCalculada linea = CalculadoraIva.linea(precioDelCatalogo, 4);

        assertThat(linea.precioUnitario()).isSameAs(precioDelCatalogo);
    }

    @Test
    void puedeCalcularConLaTasaGuardadaEnUnaVentaPasada() {   // HU-04.01 crit. 3
        LineaCalculada linea = CalculadoraIva.linea(new BigDecimal("5.50"), 2, TasaIva.de(new BigDecimal("0.10")));

        assertThat(linea.base()).isEqualTo(new BigDecimal("10.000000"));
        assertThat(linea.iva()).isEqualTo(new BigDecimal("1.000000"));
    }

    @Test
    void rechazaCantidadesNoPositivasYDatosFaltantes() {   // HU-06.04 crit. 3
        assertThatThrownBy(() -> CalculadoraIva.linea(BigDecimal.ONE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraIva.linea(BigDecimal.ONE, -2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraIva.linea(null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CalculadoraIva.linea(BigDecimal.ONE, 1, null)).isInstanceOf(NullPointerException.class);
    }
}
