package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-08.03 crit. 3: la certificación no demora la atención más allá del tiempo límite configurado. */
class LlamadaConLimiteTest {

    @Test
    void devuelveLaRespuestaQueLlegaATiempo() {
        assertThat(LlamadaConLimite.conLimite(() -> "certificada", Duration.ofSeconds(1))).isEqualTo("certificada");
    }

    @Test
    void cortaLaEsperaAlVencerElLimite() {
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> LlamadaConLimite.conLimite(() -> {
            dormir(Duration.ofSeconds(5));
            return "tarde";
        }, Duration.ofMillis(200)))
                .isInstanceOf(CertificadorNoDisponibleException.class)
                .hasMessage("El certificador no respondió en 200 ms.");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void elMensajeDiceElLimiteEnSegundos() {
        assertThatThrownBy(() -> LlamadaConLimite.conLimite(() -> {
            dormir(Duration.ofSeconds(5));
            return "tarde";
        }, Duration.ofSeconds(1))).hasMessage("El certificador no respondió en 1 segundo.");
    }

    @Test
    void lasFallasDelCertificadorLleganTalCual() {
        assertThatThrownBy(() -> LlamadaConLimite.conLimite(() -> {
            throw new CertificadorNoDisponibleException("Sin conexión.");
        }, Duration.ofSeconds(1))).isInstanceOf(CertificadorNoDisponibleException.class).hasMessage("Sin conexión.");
    }

    private static void dormir(Duration duracion) {
        try {
            Thread.sleep(duracion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
