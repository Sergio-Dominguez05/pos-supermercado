package gt.supermercado.pos.configuracion.aplicacion;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Valor por defecto de los parámetros mientras HU-12.13 (#119) no permita editarlos. */
class ParametrosTest {

    @Test
    void usaLaCantidadMaximaGuardada() {
        assertThat(new Parametros(clave -> Optional.of(12)).cantidadMaximaPorLinea()).isEqualTo(12);
    }

    @Test
    void sinElParametroOConUnValorNoValidoUsaElValorPorDefecto() {   // HU-06.04 crit. 4
        assertThat(new Parametros(clave -> Optional.empty()).cantidadMaximaPorLinea()).isEqualTo(50);
        assertThat(new Parametros(clave -> Optional.of(0)).cantidadMaximaPorLinea()).isEqualTo(50);
    }
}
