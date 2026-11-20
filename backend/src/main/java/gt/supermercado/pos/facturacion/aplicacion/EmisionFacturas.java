package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.Factura;
import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.facturacion.dominio.GeneradorDte;
import gt.supermercado.pos.facturacion.dominio.MotivoRechazo;
import gt.supermercado.pos.facturacion.dominio.ReceptorDeVenta;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.facturacion.dominio.ResultadoCertificacion;
import gt.supermercado.pos.facturacion.dominio.TraductorErrores;
import gt.supermercado.pos.impuestos.dominio.Totales;
import gt.supermercado.pos.pagos.dominio.EmisionDeFactura;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-08.03 Certificar la factura al finalizar la venta (Architecture.md §6.2).
 * <ul>
 *   <li>Crit. 1: al finalizar el cobro (HU-07.08) se arma el DTE (HU-08.02) y se envía al certificador.</li>
 *   <li>Crit. 2: si lo acepta, se guardan la autorización (UUID), la serie, el número y la fecha de certificación.</li>
 *   <li>Crit. 3: la llamada se corta al vencer el tiempo límite configurado (HU-12.03); sin respuesta, la factura pasa
 *       a contingencia (HU-08.07) y la venta ya está guardada.</li>
 *   <li>Crit. 4: la factura queda asociada a su venta, y por ella a la caja, el turno y el usuario.</li>
 * </ul>
 * HU-08.06: un rechazo se traduce a un motivo comprensible ({@link TraductorErrores}), dice si se corrige en caja y
 * queda en la bitácora con su detalle; HU-04.09 crit. 2: si es por montos, con los montos que envió el POS.
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
    private final GestionVentas ventas;
    private final Bitacora bitacora;

    public EmisionFacturas(GeneracionDte generacion, RepositorioFacturas facturas, CertificadorFel certificador,
                           GestionConexionFel conexion, ReceptorDeVenta receptores, GestionVentas ventas,
                           Bitacora bitacora) {
        this.generacion = generacion;
        this.facturas = facturas;
        this.certificador = certificador;
        this.conexion = conexion;
        this.receptores = receptores;
        this.ventas = ventas;
        this.bitacora = bitacora;
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
    public FacturaDeVenta certificar(Venta venta, SesionActiva sesion) {
        return enviar(facturaDe(venta.id()), venta, false, sesion);
    }

    /**
     * HU-08.04 crit. 2: vuelve a certificar una factura que quedó sin respuesta (pendiente o en contingencia). Como la
     * respuesta anterior pudo perderse, primero pregunta al certificador por el identificador de la venta y solo la
     * envía si no la tiene. Lo usan el reenvío de una factura rechazada (HU-08.06) y la certificación de contingencias
     * (HU-08.08).
     */
    public FacturaDeVenta reintentar(long ventaId, SesionActiva sesion) {
        return enviar(facturaDe(ventaId), ventas.porId(ventaId), true, sesion);
    }

    /**
     * Crit. 1: el identificador único es el de la venta, así que un reenvío no genera otra factura. Crit. 3: una
     * factura certificada (o rechazada, o anulada) no se vuelve a enviar.
     */
    private FacturaDeVenta enviar(Factura factura, Venta venta, boolean consultarAntes, SesionActiva sesion) {
        if (factura.estado() != EstadoFactura.PENDIENTE && factura.estado() != EstadoFactura.CONTINGENCIA) {
            return actual(factura);
        }
        ResultadoCertificacion resultado;
        try {
            resultado = LlamadaConLimite.conLimite(() -> consultarAntes
                    ? certificador.consultarPorIdentificador(factura.identificador())
                            .orElseGet(() -> certificador.certificar(factura.xmlEnviado(), factura.identificador()))
                    : certificador.certificar(factura.xmlEnviado(), factura.identificador()), limite());
        } catch (CertificadorNoDisponibleException e) {
            return contingencia(factura, venta, e.getMessage());
        } catch (RuntimeException e) {
            // La venta ya está cobrada: cualquier falla del envío deja la factura para reintentarla, nunca un error.
            LOG.warn("Falló el envío de la factura {} al certificador", factura.id(), e);
            return contingencia(factura, venta, "Falló la comunicación con el certificador.");
        }
        if (resultado.certificada()) {
            if (!facturas.certificada(factura.id(), resultado)) {
                return actual(facturaDe(factura.ventaId()));
            }
            return new FacturaDeVenta(EstadoFactura.CERTIFICADA.name(), resultado.autorizacion(), resultado.serie(),
                    resultado.numero(), resultado.fechaCertificacion(), null, false);
        }
        if (!facturas.rechazada(factura.id(), resultado)) {
            return actual(facturaDe(factura.ventaId()));
        }
        MotivoRechazo motivo = TraductorErrores.traducir(resultado.codigoError(), resultado.mensajeError());
        registrarRechazo(venta, factura, motivo, sesion);
        return rechazada(motivo);
    }

    /** HU-08.06 y HU-04.09 crit. 2: el rechazo queda en la bitácora con el detalle para corregirlo. */
    private void registrarRechazo(Venta venta, Factura factura, MotivoRechazo motivo, SesionActiva sesion) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("venta", venta.numero());
        detalle.put("tipo", motivo.tipo().name());
        detalle.put("corregibleEnCaja", motivo.corregibleEnCaja());
        detalle.put("codigo", motivo.codigo());
        detalle.put("mensajeCertificador", motivo.detalle());
        if (motivo.tipo() == MotivoRechazo.Tipo.MONTOS) {
            Totales totales = venta.totales();
            detalle.put("subtotalEnviado", totales.subtotal());
            detalle.put("ivaEnviado", totales.iva());
            detalle.put("totalEnviado", totales.total());
            detalle.put("lineas", venta.lineas().size());
        }
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.FACTURA_RECHAZADA_CERTIFICADOR, true,
                "Factura de la venta N.º %d rechazada por el certificador: %s".formatted(venta.numero(),
                        motivo.mensaje()), "factura", factura.id(), detalle));
    }

    /**
     * HU-08.07 crit. 1 y 4: sin respuesta, la factura queda guardada en contingencia. La primera vez recibe su número de
     * acceso y se arma otra vez el DTE con ese número (con la copia del emisor guardada en la factura), que es el que
     * se certificará después (HU-08.08).
     */
    private FacturaDeVenta contingencia(Factura factura, Venta venta, String motivo) {
        Long nuevo = null;
        String xml = null;
        if (factura.numeroAcceso() == null) {
            nuevo = facturas.siguienteNumeroAcceso();
            FacturaImprimible copia = facturas.imprimible(factura.ventaId()).orElseThrow();
            xml = GeneradorDte.generar(new DatosDte(copia.emisor(), copia.establecimiento(), copia.receptor(),
                    factura.emitidaEn(), venta, nuevo));
        }
        if (!facturas.enContingencia(factura.id(), motivo, nuevo, xml)) {
            return actual(facturaDe(factura.ventaId()));
        }
        long numeroAcceso = nuevo != null ? nuevo : factura.numeroAcceso();
        return new FacturaDeVenta(EstadoFactura.CONTINGENCIA.name(), null, null, null, null, motivo
                + " Se emitió el comprobante en contingencia con el número de acceso %d; se certificará después."
                .formatted(numeroAcceso), false);
    }

    private static FacturaDeVenta rechazada(MotivoRechazo motivo) {
        return new FacturaDeVenta(EstadoFactura.RECHAZADA.name(), null, null, null, null, motivo.mensaje(),
                motivo.corregibleEnCaja());
    }

    /** La factura tal como está; si está rechazada, con su motivo. */
    private FacturaDeVenta actual(Factura factura) {
        if (factura.estado() == EstadoFactura.RECHAZADA) {
            return facturas.rechazoDe(factura.id()).map(r -> rechazada(r.motivo()))
                    .orElse(new FacturaDeVenta(factura.estado().name(), null, null, null, null, null, false));
        }
        return new FacturaDeVenta(factura.estado().name(), factura.autorizacion(), factura.serie(), factura.numero(),
                factura.fechaCertificacion(), null, false);
    }

    private Factura facturaDe(long ventaId) {
        return facturas.deLaVenta(ventaId).orElseThrow(() -> new IllegalStateException(
                "La venta %d no tiene factura.".formatted(ventaId)));
    }

    private Duration limite() {
        return conexion.configuracion().map(ConfiguracionFel::timeoutMs).map(Duration::ofMillis)
                .orElse(LIMITE_POR_DEFECTO);
    }
}
