package gt.supermercado.pos.impresion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.facturacion.aplicacion.ConsultaFacturas;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import gt.supermercado.pos.impresion.dominio.RepositorioReimpresiones;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos;
import gt.supermercado.pos.pagos.dominio.ResultadoImpresion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-08.12 Reimprimir una factura.
 * <ul>
 *   <li>Crit. 1: la última factura certificada de la caja del equipo, con un atajo (F9).</li>
 *   <li>Crit. 2: las facturas anteriores, según los permisos: el cajero, las de su turno abierto; quien gestiona
 *       facturas (Supervisor y Administrador), cualquiera.</li>
 *   <li>Crit. 3: el ticket lleva la leyenda "COPIA - REIMPRESIÓN" y sale una sola copia.</li>
 *   <li>Crit. 4: cada reimpresión queda en la tabla {@code reimpresion} y en la bitácora; también el intento fallido.</li>
 * </ul>
 * Sale en la impresora de la caja desde donde se pide.
 */
@Service
public class ReimpresionFacturas {

    /** Quien tiene este permiso reimprime facturas de cualquier turno (crit. 2). */
    static final String GESTIONA_FACTURAS = "FACTURA_ANULAR";

    private final ConsultaFacturas facturas;
    private final ImpresionFacturas impresion;
    private final GestionImpresoras impresoras;
    private final GestionVentas ventas;
    private final GestionPagos pagos;
    private final GestionTurnos turnos;
    private final RepositorioReimpresiones reimpresiones;
    private final Bitacora bitacora;

    public ReimpresionFacturas(ConsultaFacturas facturas, ImpresionFacturas impresion, GestionImpresoras impresoras,
                               GestionVentas ventas, GestionPagos pagos, GestionTurnos turnos,
                               RepositorioReimpresiones reimpresiones, Bitacora bitacora) {
        this.facturas = facturas;
        this.impresion = impresion;
        this.impresoras = impresoras;
        this.ventas = ventas;
        this.pagos = pagos;
        this.turnos = turnos;
        this.reimpresiones = reimpresiones;
        this.bitacora = bitacora;
    }

    /** Crit. 2: las facturas que esta sesión puede reimprimir. */
    public List<FacturaEmitida> reimprimibles(String texto, LocalDate fecha, SesionActiva sesion) {
        Long turnoId = sesion.permisos().contains(GESTIONA_FACTURAS) ? null : turnos.exigirTurnoAbierto(sesion).id();
        return facturas.certificadas(texto, fecha, turnoId);
    }

    /** Crit. 1: la última factura certificada de la caja de este equipo. */
    public ResultadoImpresion reimprimirUltima(SesionActiva sesion) {
        if (sesion.cajaId() == null) {
            throw new NegocioException("EQUIPO_SIN_CAJA",
                    "Este equipo no está vinculado a una caja: no hay una última factura que reimprimir.");
        }
        FacturaEmitida factura = facturas.ultimaCertificadaDeLaCaja(sesion.cajaId()).orElseThrow(() ->
                new NegocioException("SIN_FACTURAS", "Esta caja todavía no tiene facturas certificadas."));
        return reimprimir(factura, sesion);
    }

    /** Crit. 2: una factura anterior, si esta sesión puede reimprimirla. */
    public ResultadoImpresion reimprimir(long facturaId, SesionActiva sesion) {
        FacturaEmitida factura = facturas.emitida(facturaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "FACTURA_NO_ENCONTRADA", "La factura no existe."));
        return reimprimir(factura, sesion);
    }

    private ResultadoImpresion reimprimir(FacturaEmitida factura, SesionActiva sesion) {
        Impresora impresora;
        try {
            if (factura.estado() != EstadoFactura.CERTIFICADA) {
                throw new NegocioException("FACTURA_NO_REIMPRIMIBLE", factura.estado() == EstadoFactura.ANULADA
                        ? "La factura está anulada: no se reimprime."
                        : "Solo se reimprime una factura certificada.");
            }
            if (!sesion.permisos().contains(GESTIONA_FACTURAS)
                    && turnos.exigirTurnoAbierto(sesion).id() != factura.turnoId()) {
                throw new NegocioException("FACTURA_DE_OTRO_TURNO",
                        "Solo puede reimprimir las facturas de su turno: pídale la reimpresión a un supervisor.");
            }
            if (sesion.cajaId() == null) {
                throw new NegocioException("EQUIPO_SIN_CAJA",
                        "Este equipo no está vinculado a una caja: no hay impresora donde reimprimir.");
            }
            impresora = impresoras.deLaCaja(sesion.cajaId()).orElseThrow(() -> new NegocioException(
                    "CAJA_SIN_IMPRESORA", "Esta caja no tiene impresora configurada."));
            Venta venta = ventas.porId(factura.ventaId());
            impresion.enviar(venta, pagos.deLaVenta(venta.id()), impresora, 1, true);
        } catch (ImpresoraNoDisponibleException e) {
            throw rechazar(sesion, factura, new NegocioException("IMPRESORA_NO_DISPONIBLE",
                    "No se pudo reimprimir: " + e.getMessage()));
        } catch (NegocioException error) {
            throw rechazar(sesion, factura, error);
        }
        reimpresiones.registrar(factura.facturaId(), sesion.usuarioId(), sesion.cajaId());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("venta", factura.ventaNumero());
        detalle.put("serie", factura.serie());
        detalle.put("numero", factura.numero());
        detalle.put("impresora", impresora.nombre());
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.FACTURA_REIMPRESA,
                true, "Factura %s-%s de la venta N.º %d reimpresa en '%s'".formatted(factura.serie(), factura.numero(),
                        factura.ventaNumero(), impresora.nombre()), "factura", factura.facturaId(), detalle)
                .enCaja(sesion.cajaId()));
        return new ResultadoImpresion(true, "Se reimprimió la factura %s-%s de la venta N.º %d."
                .formatted(factura.serie(), factura.numero(), factura.ventaNumero()));
    }

    private NegocioException rechazar(SesionActiva sesion, FacturaEmitida factura, NegocioException error) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.FACTURA_REIMPRESA, false, "Reimpresión de la factura de la venta N.º %d rechazada: %s"
                        .formatted(factura.ventaNumero(), error.getMessage()), "factura", factura.facturaId(), null)
                .enCaja(sesion.cajaId()));
        return error;
    }
}
