package gt.supermercado.pos.facturacion.infraestructura.fel;

import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConexionFel;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import gt.supermercado.pos.facturacion.infraestructura.fel.CertificadorFelSimulado.Escenario;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificadorFelSimuladoTest {

    private final CertificadorFelSimulado certificador = new CertificadorFelSimulado(Escenario.ACEPTAR);

    @Test
    void reenviarElMismoIdentificadorNoGeneraOtraFactura() {
        UUID venta = UUID.randomUUID();

        ResultadoCertificacion primera = certificador.certificar("<dte/>", venta);
        ResultadoCertificacion reintento = certificador.certificar("<dte/>", venta);

        assertThat(primera.certificada()).isTrue();
        assertThat(reintento.autorizacion()).isEqualTo(primera.autorizacion());
        assertThat(certificador.consultarPorIdentificador(venta)).contains(primera);
    }

    @Test
    void escenarioRechazarDevuelveElMotivo() {
        certificador.usarEscenario(Escenario.RECHAZAR);

        ResultadoCertificacion resultado = certificador.certificar("<dte/>", UUID.randomUUID());

        assertThat(resultado.certificada()).isFalse();
        assertThat(resultado.mensajeError()).isNotBlank();
    }

    @Test
    void laPruebaDeConexionSigueElEscenarioSinEmitirFacturas() {   // HU-08.01 crit. 3
        ConexionFel conexion = new ConexionFel("Simulado", "https://simulado.test", "usuario", "llave",
                AmbienteFel.PRUEBAS, 2000);

        assertThat(certificador.probarConexion(conexion).mensaje()).contains("ambiente de pruebas");
        certificador.usarEscenario(Escenario.RECHAZAR);
        assertThat(certificador.probarConexion(conexion).exitosa()).isFalse();
        certificador.usarEscenario(Escenario.SIN_CONEXION);
        assertThatThrownBy(() -> certificador.probarConexion(conexion))
                .isInstanceOf(CertificadorNoDisponibleException.class);
    }

    @Test
    void escenarioSinConexionPermiteProbarLaContingencia() {
        certificador.usarEscenario(Escenario.SIN_CONEXION);

        assertThatThrownBy(() -> certificador.certificar("<dte/>", UUID.randomUUID()))
                .isInstanceOf(CertificadorNoDisponibleException.class);
    }

    @Test
    void escenarioRespuestaPerdidaCertificaPeroNoResponde() {   // HU-08.04 crit. 2
        UUID venta = UUID.randomUUID();
        certificador.usarEscenario(Escenario.RESPUESTA_PERDIDA);

        assertThatThrownBy(() -> certificador.certificar("<dte/>", venta))
                .isInstanceOf(CertificadorNoDisponibleException.class);

        certificador.usarEscenario(Escenario.ACEPTAR);
        assertThat(certificador.consultarPorIdentificador(venta)).hasValueSatisfying(
                resultado -> assertThat(resultado.certificada()).isTrue());
        assertThat(certificador.enviosDe(venta)).isEqualTo(1);
    }

    @Test
    void soloSePuedeAnularUnaFacturaCertificada() {
        ResultadoCertificacion certificada = certificador.certificar("<dte/>", UUID.randomUUID());

        assertThat(certificador.anular(certificada.autorizacion(), "Error de digitación").aceptada()).isTrue();
        assertThat(certificador.anular(UUID.randomUUID(), "No existe").aceptada()).isFalse();
    }
}
