package gt.supermercado.pos.facturacion.infraestructura.fel;

import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConexionFel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-08.01: el adaptador REST genérico, mientras no se elige el certificador. */
class CertificadorFelRestTest {

    private final CertificadorFelRest certificador = new CertificadorFelRest();

    @Test
    void soloHablaPorHttps() {   // crit. 2
        assertThat(certificador.probarConexion(conexion("http://certificador.test/api")).exitosa()).isFalse();
    }

    @Test
    void sinRespuestaElCertificadorNoEstaDisponible() {   // crit. 3
        // El puerto 1 de la propia máquina rechaza la conexión al instante.
        assertThatThrownBy(() -> certificador.probarConexion(conexion("https://127.0.0.1:1/api")))
                .isInstanceOf(CertificadorNoDisponibleException.class)
                .hasMessageContaining("127.0.0.1");
    }

    @Test
    void laRespuestaDelCertificadorSeTraduceAUnMensajeComprensible() {
        assertThat(CertificadorFelRest.resultado(200).exitosa()).isTrue();
        assertThat(CertificadorFelRest.resultado(401).mensaje()).contains("rechazó el usuario o la llave");
        assertThat(CertificadorFelRest.resultado(403).exitosa()).isFalse();
        assertThat(CertificadorFelRest.resultado(404).mensaje()).contains("HTTP 404", "revise la dirección");
    }

    @Test
    void lasDemasOperacionesEsperanLaEleccionDelCertificador() {
        assertThatThrownBy(() -> certificador.certificar("<dte/>", UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("falta elegir el certificador");
    }

    @Test
    void laConexionNoMuestraLaLlaveAlImprimirse() {   // HU-12.15 crit. 2
        assertThat(conexion("https://certificador.test").toString()).doesNotContain("llave-secreta");
    }

    private static ConexionFel conexion(String url) {
        return new ConexionFel("Certificador de prueba", url, "usuario", "llave-secreta", AmbienteFel.PRUEBAS, 2000);
    }
}
