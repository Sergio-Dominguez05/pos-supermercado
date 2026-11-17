package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.Factura;
import gt.supermercado.pos.facturacion.dominio.GeneradorDte;
import gt.supermercado.pos.facturacion.dominio.ReceptorDeVenta;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import gt.supermercado.pos.pagos.dominio.EmisionDeFactura;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * HU-08.03 Certificar la factura al finalizar la venta (Architecture.md §6.2).
 * <ul>
 *   <li>Crit. 1: al finalizar el cobro (HU-07.08) se arma el DTE (HU-08.02) y se envía al certificador.</li>
 *   <li>Crit. 2: si lo acepta, se guardan la autorización (UUID), la serie, el número y la fecha de certificación.</li>
 *   <li>Crit. 3: la llamada se corta al vencer el tiempo límite configurado (HU-12.03); sin respuesta, la factura pasa
 *       a contingencia (HU-08.07) y la venta ya está guardada.</li>
 *   <li>Crit. 4: la factura queda asociada a su venta, y por ella a la caja, el turno y el usuario.</li>
 * </ul>
 * La factura va al cliente asignado a la venta (EP-05) o a Consumidor Final ({@link ReceptorDeVenta}).
 */
@Service
public class EmisionFacturas implements EmisionDeFactura {

    /** El tiempo límite si todavía no se configuró la conexión (el mismo valor por defecto de {@code config_fel}). */
    static final Duration LIMITE_POR_DEFECTO = Duration.ofMillis(8000);

    private static final Logger LOG = LoggerFactory.getLogger(EmisionFacturas.class);

    private final GeneracionDte generacion;
    private final RepositorioFacturas facturas;
    private final CertificadorFel certificador;
    private final GestionConexionFel conexion;
    private final ReceptorDeVenta receptores;

    public EmisionFacturas(GeneracionDte generacion, RepositorioFacturas facturas, CertificadorFel certificador,
                           GestionConexionFel conexion, ReceptorDeVenta receptores) {
        this.generacion = generacion;
        this.facturas = facturas;
        this.certificador = certificador;
        this.conexion = conexion;
        this.receptores = receptores;
    }

    /**
     * Guarda la factura {@code PENDIENTE} con el XML del DTE y la tarea de certificarla, en la transacción que
     * finaliza la venta. Si falta configurar el emisor o el establecimiento, lanza la {@code NegocioException} del
     * generador y la venta no se finaliza.
     */
    @Override
    @Transactional
    public void emitir(Venta venta) {
        DatosDte datos = generacion.datos(venta, receptores.de(venta.id()), OffsetDateTime.now());
        facturas.registrar(datos, GeneradorDte.generar(datos));
    }

    /** Envía la factura pendiente al certificador, después del {@code COMMIT}. Nunca lanza por una falla del envío. */
    @Override
    public FacturaDeVenta certificar(Venta venta) {
        return enviar(facturaDe(venta.id()), false);
    }

    /**
     * HU-08.04 crit. 2: vuelve a certificar una factura que quedó sin respuesta (pendiente o en contingencia). Como la
     * respuesta anterior pudo perderse, primero pregunta al certificador por el identificador de la venta y solo la
     * envía si no la tiene. Lo usan la certificación de contingencias (HU-08.08) y el reenvío (HU-08.06).
     */
    public FacturaDeVenta reintentar(long ventaId) {
        return enviar(facturaDe(ventaId), true);
    }

    /**
     * Crit. 1: el identificador único es el de la venta, así que un reenvío no genera otra factura. Crit. 3: una
     * factura certificada (o rechazada, o anulada) no se vuelve a enviar.
     */
    private FacturaDeVenta enviar(Factura factura, boolean consultarAntes) {
        if (factura.estado() != EstadoFactura.PENDIENTE && factura.estado() != EstadoFactura.CONTINGENCIA) {
            return deFactura(factura);
        }
        ResultadoCertificacion resultado;
        try {
            resultado = LlamadaConLimite.conLimite(() -> consultarAntes
                    ? certificador.consultarPorIdentificador(factura.identificador())
                            .orElseGet(() -> certificador.certificar(factura.xmlEnviado(), factura.identificador()))
                    : certificador.certificar(factura.xmlEnviado(), factura.identificador()), limite());
        } catch (CertificadorNoDisponibleException e) {
            return contingencia(factura, e.getMessage());
        } catch (RuntimeException e) {
            // La venta ya está cobrada: cualquier falla del envío deja la factura para reintentarla, nunca un error.
            LOG.warn("Falló el envío de la factura {} al certificador", factura.id(), e);
            return contingencia(factura, "Falló la comunicación con el certificador.");
        }
        if (resultado.certificada()) {
            if (!facturas.certificada(factura.id(), resultado)) {
                return deFactura(facturaDe(factura.ventaId()));
            }
            return new FacturaDeVenta(EstadoFactura.CERTIFICADA.name(), resultado.autorizacion(), resultado.serie(),
                    resultado.numero(), resultado.fechaCertificacion(), null);
        }
        if (!facturas.rechazada(factura.id(), resultado)) {
            return deFactura(facturaDe(factura.ventaId()));
        }
        return new FacturaDeVenta(EstadoFactura.RECHAZADA.name(), null, null, null, null,
                "El certificador rechazó la factura: " + resultado.mensajeError());
    }

    private FacturaDeVenta contingencia(Factura factura, String motivo) {
        if (!facturas.enContingencia(factura.id(), motivo)) {
            return deFactura(facturaDe(factura.ventaId()));
        }
        return new FacturaDeVenta(EstadoFactura.CONTINGENCIA.name(), null, null, null, null, motivo
                + " La factura queda en contingencia y se certificará después.");
    }

    private Factura facturaDe(long ventaId) {
        return facturas.deLaVenta(ventaId).orElseThrow(() -> new IllegalStateException(
                "La venta %d no tiene factura.".formatted(ventaId)));
    }

    private Duration limite() {
        return conexion.configuracion().map(ConfiguracionFel::timeoutMs).map(Duration::ofMillis)
                .orElse(LIMITE_POR_DEFECTO);
    }

    private static FacturaDeVenta deFactura(Factura factura) {
        return new FacturaDeVenta(factura.estado().name(), factura.autorizacion(), factura.serie(), factura.numero(),
                factura.fechaCertificacion(), null);
    }
}
