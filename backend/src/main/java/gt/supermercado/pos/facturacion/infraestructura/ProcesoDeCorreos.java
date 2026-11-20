package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.aplicacion.EnvioFacturas;
import gt.supermercado.pos.facturacion.aplicacion.EnvioFacturas.Ronda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * HU-08.15 crit. 3 (Architecture.md §6.4): cada minuto envía los correos pendientes y reintenta los fallidos. Se
 * apaga con {@code pos.correo.automatico=false} (en las pruebas, para que no compita con ellas).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pos.correo.automatico", havingValue = "true", matchIfMissing = true)
class ProcesoDeCorreos {

    private static final Logger LOG = LoggerFactory.getLogger(ProcesoDeCorreos.class);

    private final EnvioFacturas envio;

    ProcesoDeCorreos(EnvioFacturas envio) {
        this.envio = envio;
    }

    @Scheduled(fixedDelayString = "${pos.correo.intervalo-ms:60000}", initialDelayString = "${pos.correo.intervalo-ms:60000}")
    void enviarPendientes() {
        try {
            Ronda ronda = envio.enviarPendientes();
            if (ronda.enviadas() + ronda.fallidas() > 0) {
                LOG.info("Correos de facturas: {} enviados, {} fallidos", ronda.enviadas(), ronda.fallidas());
            }
        } catch (RuntimeException e) {
            LOG.warn("Falló el envío automático de correos", e);
        }
    }
}
