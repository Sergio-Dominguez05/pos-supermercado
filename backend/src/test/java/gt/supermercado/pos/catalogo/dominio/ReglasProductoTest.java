package gt.supermercado.pos.catalogo.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03.01 Registrar un producto: formato de los datos. */
class ReglasProductoTest {

    // Crit. 1 y 3: código de barras, también el que deja el lector

    @Test
    void elCodigoLeidoPorElLectorSeGuardaSinEspaciosNiSaltosDeLinea() {
        assertThat(ReglasProducto.normalizarCodigo(" 7401234567890\r\n")).isEqualTo("7401234567890");
        assertThat(ReglasProducto.normalizarCodigo("abc-12")).isEqualTo("ABC-12");
        assertThat(ReglasProducto.normalizarCodigo(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7401234567890", "012345678905", "12345678901231", "INT-0001", "1"})
    void aceptaCodigosEanUpcEInternos(String codigo) {
        assertThat(ReglasProducto.esCodigoValido(codigo)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "74012/3", "CÓDIGO", "A_1"})
    void rechazaCodigosVaciosOConCaracteresNoPermitidos(String codigo) {
        assertThat(ReglasProducto.esCodigoValido(codigo)).isFalse();
    }

    @Test
    void elCodigoTieneComoMaximo50Caracteres() {
        assertThat(ReglasProducto.esCodigoValido("9".repeat(50))).isTrue();
        assertThat(ReglasProducto.esCodigoValido("9".repeat(51))).isFalse();
    }

    // Crit. 5: descripción obligatoria, máximo 100 caracteres

    @Test
    void laDescripcionEsObligatoriaYTieneComoMaximo100Caracteres() {
        assertThat(ReglasProducto.esDescripcionValida(ReglasProducto.normalizarDescripcion("   "))).isFalse();
        assertThat(ReglasProducto.esDescripcionValida(ReglasProducto.normalizarDescripcion(null))).isFalse();
        assertThat(ReglasProducto.esDescripcionValida("x".repeat(100))).isTrue();
        assertThat(ReglasProducto.esDescripcionValida("x".repeat(101))).isFalse();
    }

    @Test
    void laDescripcionSeGuardaConUnSoloEspacioEntrePalabras() {
        assertThat(ReglasProducto.normalizarDescripcion("  Leche   entera 1 L ")).isEqualTo("Leche entera 1 L");
    }

    // Crit. 4: precio mayor que cero, máximo 2 decimales

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "5", "5.1", "11.20", "11.200", "9999999999.99"})
    void aceptaPreciosPositivosConHastaDosDecimales(String precio) {
        assertThat(ReglasProducto.esPrecioValido(new BigDecimal(precio))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "5.105", "0.001", "10000000000.00"})
    void rechazaPreciosEnCeroNegativosConMasDecimalesOFueraDeRango(String precio) {
        assertThat(ReglasProducto.esPrecioValido(new BigDecimal(precio))).isFalse();
    }

    @Test
    void sinPrecioNoEsValido() {
        assertThat(ReglasProducto.esPrecioValido(null)).isFalse();
    }

    @Test
    void elPrecioSeGuardaConDosDecimales() {
        assertThat(ReglasProducto.normalizarPrecio(new BigDecimal("5.1"))).isEqualTo(new BigDecimal("5.10"));
        assertThat(ReglasProducto.normalizarPrecio(new BigDecimal("11.200"))).isEqualTo(new BigDecimal("11.20"));
    }
}
