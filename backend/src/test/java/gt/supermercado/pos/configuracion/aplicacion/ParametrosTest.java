package gt.supermercado.pos.configuracion.aplicacion;

import org.junit.jupiter.api.Test;
import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura segura de los parámetros de caja y venta (HU-12.13). */
class ParametrosTest {

    @Test
    void usaLaCantidadMaximaGuardada() {
        assertThat(new Parametros(new RepositorioPrueba(Optional.of(12), Optional.empty()))
                .cantidadMaximaPorLinea()).isEqualTo(12);
    }

    @Test
    void sinElParametroOConUnValorNoValidoUsaElValorPorDefecto() {   // HU-06.04 crit. 4
        assertThat(new Parametros(new RepositorioPrueba(Optional.empty(), Optional.empty()))
                .cantidadMaximaPorLinea()).isEqualTo(50);
        assertThat(new Parametros(new RepositorioPrueba(Optional.of(0), Optional.empty()))
                .cantidadMaximaPorLinea()).isEqualTo(50);
        assertThat(new Parametros(new RepositorioPrueba(Optional.of(10_000), Optional.empty()))
                .cantidadMaximaPorLinea()).isEqualTo(50);
    }

    @Test
    void elCierreCiegoEstaDesactivadoSiNoHayUnValorGuardado() {
        assertThat(new Parametros(new RepositorioPrueba(Optional.empty(), Optional.of(true))).cierreCiego()).isTrue();
        assertThat(new Parametros(new RepositorioPrueba(Optional.empty(), Optional.empty())).cierreCiego()).isFalse();
    }

    private record RepositorioPrueba(Optional<Integer> entero, Optional<Boolean> booleano)
            implements RepositorioParametros {

        @Override
        public Optional<Integer> entero(String clave) {
            return entero;
        }

        @Override
        public Optional<Boolean> booleano(String clave) {
            return booleano;
        }

        @Override
        public void guardarEntero(String clave, int valor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void guardarBooleano(String clave, boolean valor) {
            throw new UnsupportedOperationException();
        }
    }
}
