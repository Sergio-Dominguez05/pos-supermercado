package gt.supermercado.pos.compartido.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-05.03: validación del NIT con el algoritmo de la SAT. */
class NitTest {

    @Test
    void elDigitoVerificadorSigueElModulo11DeLaSat() {   // crit. 1
        assertThat(Nit.verificador("123456")).isEqualTo('0');
        assertThat(Nit.verificador("1234567")).isEqualTo('9');
        assertThat(Nit.verificador("576937")).isEqualTo('K');   // 10 se escribe K
        assertThat(Nit.verificador("4800")).isEqualTo('3');
    }

    @Test
    void unNitConElVerificadorCorrectoEsValido() {   // crit. 1
        for (String nit : new String[] {"1234560", "12345679", "576937K", "48003"}) {
            assertThat(Nit.esValido(nit)).as(nit).isTrue();
        }
    }

    @Test
    void unNitMalEscritoNoEsValido() {   // crit. 2
        for (String nit : new String[] {"1234561", "576937", "5769370", "K", "", null, "12A4560", "CF",
                "12345678901234"}) {
            assertThat(Nit.esValido(nit)).as(String.valueOf(nit)).isFalse();
        }
    }

    @Test
    void seGuardaSinGuionesNiEspaciosYEnMayusculas() {   // crit. 3
        assertThat(Nit.validar(" 576937-k ")).contains("576937K");
        assertThat(Nit.validar("123 456-0")).contains("1234560");
        assertThat(Nit.normalizar("12-34 5")).isEqualTo("12345");
    }
}
