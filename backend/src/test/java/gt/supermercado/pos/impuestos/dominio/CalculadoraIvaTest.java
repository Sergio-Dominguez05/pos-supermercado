package gt.supermercado.pos.impuestos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04.02 Calcular base imponible e IVA desde el precio con IVA. */
class CalculadoraIvaTest {

    @Test
    void unPrecioDeQ11_20SeDesglosaEnQ10DeBaseYQ1_20DeIva() {   // crit. 1
        DesgloseIva desglose = CalculadoraIva.desglosar(new BigDecimal("11.20"));

        assertThat(desglose.base()).isEqualByComparingTo("10.00");
        assertThat(desglose.iva()).isEqualByComparingTo("1.20");
    }

    @Test
    void unPrecioDeQ5SeDesglosaConSeisDecimales() {   // crit. 2
        DesgloseIva desglose = CalculadoraIva.desglosar(new BigDecimal("5.00"));

        assertThat(desglose.base()).isEqualTo(new BigDecimal("4.464286"));
        assertThat(desglose.iva()).isEqualTo(new BigDecimal("0.535714"));
    }

    @Test
    void elDesgloseNoModificaElPrecioConIva() {   // crit. 3
        BigDecimal precioDelCatalogo = new BigDecimal("5.00");

        DesgloseIva desglose = CalculadoraIva.desglosar(precioDelCatalogo);

        assertThat(desglose.precioConIva()).isSameAs(precioDelCatalogo);
        assertThat(precioDelCatalogo).isEqualTo(new BigDecimal("5.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.99", "1.00", "3.33", "5.00", "7.77", "11.20", "19.99", "123.45", "9999.99"})
    void laBaseMasElIvaSiempreSumanElPrecioExacto(String precio) {   // crit. 3 y 4
        BigDecimal precioConIva = new BigDecimal(precio);

        DesgloseIva desglose = CalculadoraIva.desglosar(precioConIva);

        assertThat(desglose.base().add(desglose.iva())).isEqualByComparingTo(precioConIva);
        assertThat(desglose.base().scale()).isEqualTo(CalculadoraIva.DECIMALES);
        assertThat(desglose.iva().scale()).isEqualTo(CalculadoraIva.DECIMALES);
    }

    @Test
    void usaAritmeticaDecimalExactaSinErroresDePuntoFlotante() {   // crit. 4
        // Con double, 0.10 + 0.20 da 0.30000000000000004; con BigDecimal el desglose cuadra exacto.
        BigDecimal precioConIva = new BigDecimal("0.10").add(new BigDecimal("0.20"));

        DesgloseIva desglose = CalculadoraIva.desglosar(precioConIva);

        assertThat(desglose.base()).isEqualTo(new BigDecimal("0.267857"));
        assertThat(desglose.iva()).isEqualTo(new BigDecimal("0.032143"));
    }

    @Test
    void puedeDesglosarConLaTasaGuardadaEnUnaVentaPasada() {   // HU-04.01 crit. 3
        DesgloseIva desglose = CalculadoraIva.desglosar(new BigDecimal("11.00"), TasaIva.de(new BigDecimal("0.10")));

        assertThat(desglose.base()).isEqualByComparingTo("10.00");
        assertThat(desglose.iva()).isEqualByComparingTo("1.00");
    }

    @Test
    void rechazaDatosFaltantes() {
        assertThatThrownBy(() -> CalculadoraIva.desglosar(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CalculadoraIva.desglosar(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
