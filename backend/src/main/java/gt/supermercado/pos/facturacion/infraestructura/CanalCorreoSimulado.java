package gt.supermercado.pos.facturacion.infraestructura;

import gt.supermercado.pos.facturacion.dominio.CanalCorreo;
import gt.supermercado.pos.facturacion.dominio.ConexionCorreo;
import gt.supermercado.pos.facturacion.dominio.CorreoNoEnviadoException;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HU-08.15: servidor de correo en memoria para desarrollar y probar sin enviar correos reales: guarda lo enviado y lo
 * anota en el registro. {@link #fallar(boolean)} simula un servidor que no responde (crit. 3).
 */
@Component
@ConditionalOnProperty(name = "pos.correo.modo", havingValue = "simulado")
public class CanalCorreoSimulado implements CanalCorreo {

    /** Un correo enviado, con el servidor por el que salió. */
    public record Envio(ConexionCorreo conexion, MensajeCorreo mensaje) {}

    private static final Logger LOG = LoggerFactory.getLogger(CanalCorreoSimulado.class);

    private final List<Envio> envios = new CopyOnWriteArrayList<>();
    private volatile boolean fallar;

    @Override
    public void enviar(ConexionCorreo conexion, MensajeCorreo mensaje) {
        if (fallar) {
            throw new CorreoNoEnviadoException("El servidor de correo %s no respondió (simulado).".formatted(
                    conexion.servidor()));
        }
        envios.add(new Envio(conexion, mensaje));
        LOG.info("Correo simulado a {}: {} ({} adjuntos)", mensaje.destinatario(), mensaje.asunto(),
                mensaje.adjuntos().size());
    }

    public List<Envio> envios() {
        return List.copyOf(envios);
    }

    /** Los correos enviados a esa dirección, del primero al último. */
    public List<MensajeCorreo> enviadosA(String destinatario) {
        return envios.stream().map(Envio::mensaje).filter(m -> m.destinatario().equals(destinatario)).toList();
    }

    public void fallar(boolean fallar) {
        this.fallar = fallar;
    }
}
