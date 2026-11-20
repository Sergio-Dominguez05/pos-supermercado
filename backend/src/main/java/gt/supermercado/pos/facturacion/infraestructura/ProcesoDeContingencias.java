package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias;
import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias.Ronda;
import gt.supermercado.pos.seguridad.aplicacion.SesionDelSistema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * HU-08.08 crit. 1 (Architecture.md §6.4): cada minuto certifica los comprobantes pendientes, a nombre del usuario
 * técnico "sistema". Se apaga con {@code pos.contingencia.automatica=false} (en las pruebas, para que no compita con
 * ellas).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pos.contingencia.automatica", havingValue = "true", matchIfMissing = true)
class ProcesoDeContingencias {

    private static final Logger LOG = LoggerFactory.getLogger(ProcesoDeContingencias.class);

    private final CertificacionContingencias contingencias;
    private final SesionDelSistema sistema;

    ProcesoDeContingencias(CertificacionContingencias contingencias, SesionDelSistema sistema) {
        this.contingencias = contingencias;
        this.sistema = sistema;
    }

    @Scheduled(fixedDelayString = "${pos.contingencia.intervalo-ms:60000}",
            initialDelayString = "${pos.contingencia.intervalo-ms:60000}")
    void certificarPendientes() {
        try {
            Ronda ronda = contingencias.certificarPendientes(sistema.sesion());
            if (ronda.procesadas() > 0) {
                LOG.info("Contingencia: {}", ronda.mensaje());
            }
        } catch (RuntimeException e) {
            LOG.warn("Falló la certificación automática de pendientes", e);
        }
    }
}
