package gt.supermercado.pos.impresion.infraestructura;

import gt.supermercado.pos.impresion.dominio.CanalImpresion;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Impresora en memoria para desarrollar y probar sin una impresora térmica: guarda lo enviado y lo anota en el
 * registro. {@link #fallar(boolean)} simula una impresora apagada (HU-08.05 crit. 6).
 */
@Component
@ConditionalOnProperty(name = "pos.impresion.modo", havingValue = "simulado")
public class CanalImpresionSimulado implements CanalImpresion {

    /** Lo que se envió a una impresora. */
    public record Envio(String host, int puerto, byte[] datos) {}

    private static final Logger LOG = LoggerFactory.getLogger(CanalImpresionSimulado.class);

    private final List<Envio> envios = new CopyOnWriteArrayList<>();
    private volatile boolean fallar;

    @Override
    public void enviar(String host, int puerto, byte[] datos) {
        if (fallar) {
            throw new ImpresoraNoDisponibleException("La impresora %s:%d no respondió (simulado).".formatted(host,
                    puerto), null);
        }
        envios.add(new Envio(host, puerto, datos.clone()));
        LOG.info("Impresión simulada: {} bytes a {}:{}", datos.length, host, puerto);
    }

    public List<Envio> envios() {
        return List.copyOf(envios);
    }

    /** Lo último enviado a esa impresora, si se le envió algo. */
    public Envio ultimoA(String host) {
        for (int i = envios.size() - 1; i >= 0; i--) {
            if (envios.get(i).host().equals(host)) {
                return envios.get(i);
            }
        }
        return null;
    }

    public void fallar(boolean fallar) {
        this.fallar = fallar;
    }
}
