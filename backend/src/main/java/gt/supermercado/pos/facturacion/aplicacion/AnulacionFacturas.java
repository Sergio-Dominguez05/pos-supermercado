package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.facturacion.dominio.CertificadorNoDisponibleException;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.facturacion.dominio.RepositorioAnulaciones;
import gt.supermercado.pos.facturacion.dominio.ResultadoAnulacion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-08.10 Anular una factura.
 * <ul>
 *   <li>Crit. 1: se busca por número, serie, autorización o fecha.</li>
 *   <li>Crit. 2: el motivo es obligatorio (de 5 a 255 caracteres).</li>
 *   <li>Crit. 3: la anulación se envía al certificador, con el tiempo límite configurado, y queda registrada con su
 *       respuesta, también cuando la rechaza o no responde.</li>
 *   <li>Crit. 4: aceptada, la factura queda {@code ANULADA}; la anulación queda en el turno de la venta, así que el
 *       cierre de esa caja (HU-09.08) y los reportes (EP-10, que excluyen las anuladas) descuentan sus montos.</li>
 *   <li>Crit. 5: solo con el permiso de anular (Supervisor y Administrador), a nombre de quien anula; queda en la
 *       bitácora, también el intento rechazado.</li>
 *   <li>Crit. 6 y decisión D2: solo mientras el turno de la venta siga abierto y dentro del mes de emisión (el plazo de
 *       la SAT, Architecture.md §1.2).</li>
 * </ul>
 */
@Service
public class AnulacionFacturas {

    static final int RESULTADOS_MAXIMOS = 50;
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private final RepositorioAnulaciones anulaciones;
    private final CertificadorFel certificador;
    private final GestionConexionFel conexion;
    private final Bitacora bitacora;
    private final JsonMapper json;

    public AnulacionFacturas(RepositorioAnulaciones anulaciones, CertificadorFel certificador,
                             GestionConexionFel conexion, Bitacora bitacora, JsonMapper json) {
        this.anulaciones = anulaciones;
        this.certificador = certificador;
        this.conexion = conexion;
        this.bitacora = bitacora;
        this.json = json;
    }

    /** Crit. 1: sin texto ni fecha, las facturas más recientes. */
    @Transactional(readOnly = true)
    public List<FacturaEmitida> buscar(String texto, LocalDate fecha) {
        return anulaciones.buscar(texto, fecha, RESULTADOS_MAXIMOS);
    }

    public FacturaEmitida anular(long facturaId, String motivo, SesionActiva sesion) {
        FacturaEmitida factura = anulaciones.porId(facturaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "FACTURA_NO_ENCONTRADA", "La factura no existe."));
        String motivoLimpio = motivo == null ? "" : motivo.strip();
        try {
            validar(factura, motivoLimpio);
        } catch (NegocioException error) {
            throw rechazar(sesion, factura, error);
        }
        ResultadoAnulacion resultado;
        try {
            resultado = LlamadaConLimite.conLimite(() -> certificador.anular(factura.autorizacion(), motivoLimpio),
                    limite());
        } catch (CertificadorNoDisponibleException e) {
            anulaciones.intentoFallido(facturaId, "SIN_RESPUESTA", respuesta(Map.of("error", e.getMessage())));
            throw rechazar(sesion, factura, new NegocioException("CERTIFICADOR_NO_DISPONIBLE",
                    "El certificador no respondió: la factura sigue vigente. Intente anularla de nuevo."));
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("aceptada", resultado.aceptada());
        respuesta.put("codigo", resultado.codigoError());
        respuesta.put("mensaje", resultado.mensajeError());
        if (!resultado.aceptada()) {
            anulaciones.intentoFallido(facturaId, resultado.codigoError(), respuesta(respuesta));
            throw rechazar(sesion, factura, new NegocioException("ANULACION_RECHAZADA",
                    "El certificador rechazó la anulación: " + resultado.mensajeError()));
        }
        if (!anulaciones.anular(facturaId, motivoLimpio, sesion.usuarioId(), factura.turnoId(), respuesta(respuesta))) {
            throw rechazar(sesion, factura, new NegocioException("FACTURA_NO_ANULABLE",
                    "La factura ya no está certificada: ya se anuló."));
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("venta", factura.ventaNumero());
        detalle.put("serie", factura.serie());
        detalle.put("numero", factura.numero());
        detalle.put("autorizacion", factura.autorizacion().toString());
        detalle.put("total", factura.total());
        detalle.put("motivo", motivoLimpio);
        detalle.put("turno", factura.turnoId());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.FACTURA_ANULADA,
                true, "Factura %s-%s de la venta N.º %d anulada: %s".formatted(factura.serie(), factura.numero(),
                        factura.ventaNumero(), motivoLimpio), "factura", facturaId, detalle));
        return anulaciones.porId(facturaId).orElseThrow();
    }

    private void validar(FacturaEmitida factura, String motivo) {
        if (factura.estado() != EstadoFactura.CERTIFICADA) {
            throw new NegocioException("FACTURA_NO_ANULABLE", factura.estado() == EstadoFactura.ANULADA
                    ? "La factura ya está anulada."
                    : "Solo se anula una factura certificada.");
        }
        if (motivo.length() < 5 || motivo.length() > 255) {
            throw new NegocioException("MOTIVO_ANULACION_REQUERIDO",
                    "Indique el motivo de la anulación (de 5 a 255 caracteres).");
        }
        if (!factura.turnoAbierto()) {
            throw new NegocioException("TURNO_DE_LA_VENTA_CERRADO",
                    "El turno de la venta ya se cerró: la factura no se puede anular.");
        }
        if (!dentroDelPlazo(factura.emitidaEn(), YearMonth.now(GUATEMALA))) {
            throw new NegocioException("PLAZO_DE_ANULACION_VENCIDO",
                    "Solo se anula una factura dentro del mes en que se emitió.");
        }
    }

    /** Crit. 6: el plazo de la SAT, dentro del mes de emisión (en la hora de Guatemala). */
    static boolean dentroDelPlazo(OffsetDateTime emitidaEn, YearMonth mesActual) {
        return YearMonth.from(emitidaEn.atZoneSameInstant(GUATEMALA)).equals(mesActual);
    }

    private NegocioException rechazar(SesionActiva sesion, FacturaEmitida factura, NegocioException error) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.FACTURA_ANULADA, false, "Anulación de la factura de la venta N.º %d rechazada: %s"
                        .formatted(factura.ventaNumero(), error.getMessage()), "factura", factura.facturaId(), null));
        return error;
    }

    private String respuesta(Map<String, Object> datos) {
        return json.writeValueAsString(datos);
    }

    private Duration limite() {
        return conexion.configuracion().map(ConfiguracionFel::timeoutMs).map(Duration::ofMillis)
                .orElse(EmisionFacturas.LIMITE_POR_DEFECTO);
    }
}
