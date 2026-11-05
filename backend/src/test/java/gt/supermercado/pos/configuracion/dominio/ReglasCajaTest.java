package gt.supermercado.pos.configuracion.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasCajaTest {

    @Test
    void elNombreQuedaSinEspaciosAlRededorYConUnoSoloEntrePalabras() {   // #136
        assertThat(ReglasCaja.normalizarNombre("  Caja   Final ")).isEqualTo("Caja Final");
        assertThat(ReglasCaja.normalizarNombre("Caja\t Norte")).isEqualTo("Caja Norte");
        assertThat(ReglasCaja.normalizarNombre("   ")).isEmpty();
    }

    @Test
    void laSerieSeGuardaEnMayusculasYVaciaEsSinSerie() {
        assertThat(ReglasCaja.normalizarSerie(" a01 ")).isEqualTo("A01");
        assertThat(ReglasCaja.normalizarSerie("  ")).isNull();
        assertThat(ReglasCaja.esSerieValida(ReglasCaja.normalizarSerie("A 01"))).isFalse();
    }
}
