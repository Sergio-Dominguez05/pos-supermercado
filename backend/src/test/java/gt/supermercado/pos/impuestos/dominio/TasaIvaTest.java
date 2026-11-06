package gt.supermercado.pos.impuestos.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04.01 Aplicar IVA del 12 % a todos los productos. */
class TasaIvaTest {

    @Test
    void laTasaVigenteEsDel12PorCiento() {   // crit. 1 y 2
        assertThat(TasaIva.VIGENTE.valor()).isEqualTo(new BigDecimal("0.1200"));
        assertThat(TasaIva.VIGENTE.porcentaje()).isEqualTo(new BigDecimal("12"));
        assertThat(TasaIva.VIGENTE.divisor()).isEqualTo(new BigDecimal("1.1200"));
    }

    @Test
    void laTasaSeGuardaConCuatroDecimalesComoEnLaVenta() {   // crit. 3: venta.tasa_iva numeric(5,4)
        assertThat(TasaIva.VIGENTE.valor().scale()).isEqualTo(TasaIva.DECIMALES);
    }

    @Test
    void unaTasaGuardadaSeReconstruyeIgualSinImportarSusCeros() {   // crit. 3
        assertThat(TasaIva.de(new BigDecimal("0.1200"))).isEqualTo(TasaIva.VIGENTE);
        assertThat(TasaIva.de(new BigDecimal("0.12"))).isEqualTo(TasaIva.VIGENTE);
    }

    @Test
    void unaVentaPasadaConservaSuTasaAunqueLaVigenteCambie() {   // crit. 3
        TasaIva guardadaEnUnaVentaPasada = TasaIva.de(new BigDecimal("0.1000"));

        assertThat(guardadaEnUnaVentaPasada).isNotEqualTo(TasaIva.VIGENTE);
        assertThat(guardadaEnUnaVentaPasada.divisor()).isEqualTo(new BigDecimal("1.1000"));
        assertThat(guardadaEnUnaVentaPasada.porcentaje()).isEqualTo(new BigDecimal("10"));
    }

    @Test
    void rechazaTasasFueraDeRango() {
        assertThatThrownBy(() -> TasaIva.de(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TasaIva.de(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TasaIva.de(BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TasaIva.de(new BigDecimal("12"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaTasasConMasDecimalesDeLosQueSeGuardan() {
        assertThatThrownBy(() -> TasaIva.de(new BigDecimal("0.12345"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(TasaIva.de(new BigDecimal("0.123400")).valor()).isEqualTo(new BigDecimal("0.1234"));
    }
}
