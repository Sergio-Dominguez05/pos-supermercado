package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.facturacion.dominio.CanalCorreo;
import gt.supermercado.pos.facturacion.dominio.ConexionCorreo;
import gt.supermercado.pos.facturacion.dominio.EnvioPendiente;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo.Adjunto;
import gt.supermercado.pos.facturacion.dominio.RepositorioEnviosCorreo;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.facturacion.dominio.RepresentacionGrafica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HU-08.15 Enviar la factura por correo (Architecture.md §6.4, enviada por el POS por decisión de Sergio).
 * <ul>
 *   <li>Crit. 1: al emitir la factura de una venta con correo (HU-05.11) queda la tarea de enviarla; se envía en cuanto
 *       se certifica, sin que la venta espere. Sin correo no se programa nada.</li>
 *   <li>Crit. 2: el correo lleva la representación gráfica en PDF y el XML del DTE (decisión de Sergio: siempre).</li>
 *   <li>Crit. 3: si el envío falla, cada intento queda registrado y se reintenta a los 1, 2, 4 y 8 minutos; la venta
 *       no se entera. Una factura en contingencia se envía cuando se certifica (HU-08.08).</li>
 *   <li>Crit. 4: desde el detalle de la factura (HU-08.14) se reenvía en el momento.</li>
 * </ul>
 */
@Service
public class EnvioFacturas {

    /** Envíos fallidos antes de dejar de reintentar; después solo queda el reenvío manual. */
    static final int INTENTOS_MAXIMOS = 5;

    static final String SIN_SERVIDOR = "Falta configurar el servidor de correo (Configuración · Correo).";

    private static final Logger LOG = LoggerFactory.getLogger(EnvioFacturas.class);

    private final RepositorioEnviosCorreo envios;
    private final RepositorioFacturas facturas;
    private final GestionServidorCorreo servidor;
    private final CanalCorreo canal;
    /** Diferida: el PDF usa las ventas y los pagos, que a su vez emiten facturas con este servicio. */
    private final ObjectProvider<RepresentacionGrafica> representacion;
    private final TransactionOperations transacciones;
    private final Executor ejecutor;

    public EnvioFacturas(RepositorioEnviosCorreo envios, RepositorioFacturas facturas, GestionServidorCorreo servidor,
                         CanalCorreo canal, ObjectProvider<RepresentacionGrafica> representacion,
                         TransactionOperations transacciones,
                         @Value("${pos.correo.envio-inmediato:asincrono}") String envioInmediato) {
        this.envios = envios;
        this.facturas = facturas;
        this.servidor = servidor;
        this.canal = canal;
        this.representacion = representacion;
        this.transacciones = transacciones;
        // En las pruebas, "sincrono" envía antes de responder para poder comprobarlo sin esperar.
        this.ejecutor = "sincrono".equals(envioInmediato) ? Runnable::run
                : Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Cómo terminó un envío. */
    public record ResultadoEnvio(boolean enviado, String mensaje) {}

    /** Una ronda del proceso automático. */
    public record Ronda(int enviadas, int fallidas) {}

    /** Crit. 1: la factura recién certificada sale por correo en segundo plano, si tiene envío pendiente. */
    public void alCertificar(long facturaId) {
        ejecutor.execute(() -> {
            try {
                procesar(facturaId, false);
            } catch (RuntimeException e) {
                LOG.warn("Falló el envío por correo de la factura {}", facturaId, e);
            }
        });
    }

    /** Crit. 3: los envíos que ya cumplieron su espera, para el proceso automático. */
    public Ronda enviarPendientes() {
        int enviadas = 0;
        int fallidas = 0;
        for (long facturaId : envios.porEnviar(20)) {
            Optional<ResultadoEnvio> resultado = procesar(facturaId, false);
            if (resultado.isPresent()) {
                if (resultado.get().enviado()) {
                    enviadas++;
                } else {
                    fallidas++;
                }
            }
        }
        return new Ronda(enviadas, fallidas);
    }

    /**
     * Crit. 4: reenvía en el momento una factura certificada al correo de su receptor. 422 si no está certificada o
     * no tiene correo; si falla, queda programada para reintentarla.
     */
    public ResultadoEnvio reenviar(long facturaId) {
        Optional<ResultadoEnvio> resultado;
        try {
            // En la misma transacción: si no se puede enviar, tampoco queda programada.
            resultado = transacciones.execute(transaccion -> {
                envios.programar(facturaId);
                return envios.bloquear(facturaId, true).flatMap(envio -> enviar(envio, true));
            });
        } catch (DataIntegrityViolationException e) {
            throw new RecursoNoEncontradoException("FACTURA_NO_ENCONTRADA", "La factura no existe.");
        }
        return resultado.orElse(new ResultadoEnvio(false,
                "La factura ya se está enviando; revise el historial en un momento."));
    }

    /** Envía la factura si su tarea está libre (y vencida, salvo un reenvío manual). Vacío si no le tocaba. */
    private Optional<ResultadoEnvio> procesar(long facturaId, boolean manual) {
        return transacciones.execute(transaccion -> envios.bloquear(facturaId, manual)
                .flatMap(envio -> enviar(envio, manual)));
    }

    private Optional<ResultadoEnvio> enviar(EnvioPendiente envio, boolean manual) {
        if (envio.estado() == EstadoFactura.ANULADA && !manual) {
            envios.completar(envio.facturaId(), "La factura se anuló antes de enviarla.");
            return Optional.empty();
        }
        if (envio.estado() != EstadoFactura.CERTIFICADA) {
            if (manual) {
                throw new NegocioException("FACTURA_NO_CERTIFICADA", "Solo se envía por correo una factura certificada.");
            }
            return Optional.empty();
        }
        if (envio.correo() == null) {
            if (manual) {
                throw new NegocioException("FACTURA_SIN_CORREO",
                        "El cliente de la factura no tiene correo registrado.");
            }
            envios.completar(envio.facturaId(), "La factura no tiene correo.");
            return Optional.empty();
        }
        Optional<ConexionCorreo> conexion = servidor.conexionVigente();
        if (conexion.isEmpty()) {
            // No es una falla del envío: queda en espera, sin gastar intentos, hasta que se configure el servidor.
            envios.reprogramar(envio.facturaId(), SIN_SERVIDOR, false);
            return Optional.of(new ResultadoEnvio(false, SIN_SERVIDOR));
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("destinatario", envio.correo());
        detalle.put("manual", manual);
        try {
            canal.enviar(conexion.get(), mensaje(envio));
        } catch (RuntimeException e) {
            String motivo = e.getMessage() == null ? "No se pudo enviar el correo." : e.getMessage();
            detalle.put("mensaje", motivo);
            envios.registrarIntento(envio.facturaId(), false, "CORREO_NO_ENVIADO", detalle);
            if (envio.intentos() + 1 >= INTENTOS_MAXIMOS) {
                envios.completar(envio.facturaId(), "Se agotaron los intentos: " + motivo);
            } else {
                envios.reprogramar(envio.facturaId(), motivo, true);
            }
            LOG.warn("No se envió por correo la factura {} a {}: {}", envio.facturaId(), envio.correo(), motivo);
            return Optional.of(new ResultadoEnvio(false, motivo + " Se volverá a intentar."));
        }
        envios.registrarIntento(envio.facturaId(), true, null, detalle);
        envios.completar(envio.facturaId(), null);
        return Optional.of(new ResultadoEnvio(true, "Factura enviada a %s.".formatted(envio.correo())));
    }

    /** Crit. 2: el asunto con la serie y el número, el PDF y el XML. */
    private MensajeCorreo mensaje(EnvioPendiente envio) {
        String comercio = facturas.imprimible(envio.ventaId())
                .map(f -> f.emisor().nombreComercial() != null ? f.emisor().nombreComercial() : f.emisor().razonSocial())
                .orElse("el supermercado");
        String nombre = "Factura-%s-%s".formatted(envio.serie(), envio.numero());
        List<Adjunto> adjuntos = new ArrayList<>();
        adjuntos.add(new Adjunto(nombre + ".pdf", "application/pdf", representacion.getObject().pdf(envio.ventaId())));
        adjuntos.add(new Adjunto(nombre + ".xml", "application/xml", envio.xml().getBytes(StandardCharsets.UTF_8)));
        String texto = """
                Estimado cliente:

                Gracias por su compra en %s. Adjuntamos su factura electrónica serie %s número %s, en PDF y en XML.

                Número de autorización: %s

                Este correo se envió automáticamente; no es necesario responderlo.
                """.formatted(comercio, envio.serie(), envio.numero(), envio.autorizacion());
        return new MensajeCorreo(envio.correo(), "Factura electrónica serie %s número %s - %s".formatted(envio.serie(),
                envio.numero(), comercio), texto, adjuntos);
    }
}
