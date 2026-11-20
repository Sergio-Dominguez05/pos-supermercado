package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.facturacion.dominio.DatosDte;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.Factura;
import gt.supermercado.pos.facturacion.dominio.FacturaRechazada;
import gt.supermercado.pos.facturacion.dominio.GeneradorDte;
import gt.supermercado.pos.facturacion.dominio.Receptor;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

/**
 * HU-08.06 Manejar rechazos del certificador.
 * <ul>
 *   <li>Crit. 2: un error del receptor se corrige en caja (otro NIT o Consumidor Final) y la factura se reenvía sin
 *       volver a cobrar: la venta ya cobrada no cambia; se arma otra vez el DTE con el receptor corregido.</li>
 *   <li>Crit. 3: los demás errores dejan la venta pendiente de facturar en la lista de rechazadas del supervisor, que la
 *       reenvía cuando se corrige la causa (por ejemplo, la configuración del emisor).</li>
 * </ul>
 * El reenvío primero consulta al certificador por el identificador de la venta (HU-08.04): nunca duplica la factura.
 * Un reenvío no permitido queda en la bitácora como intento fallido.
 */
@Service
public class ReenvioFacturas {

    private final RepositorioFacturas facturas;
    private final GeneracionDte generacion;
    private final EmisionFacturas emision;
    private final GestionVentas ventas;
    private final GestionTurnos turnos;
    private final Bitacora bitacora;
    private final TransactionOperations transacciones;

    public ReenvioFacturas(RepositorioFacturas facturas, GeneracionDte generacion, EmisionFacturas emision,
                           GestionVentas ventas, GestionTurnos turnos, Bitacora bitacora,
                           TransactionOperations transacciones) {
        this.facturas = facturas;
        this.generacion = generacion;
        this.emision = emision;
        this.ventas = ventas;
        this.turnos = turnos;
        this.bitacora = bitacora;
        this.transacciones = transacciones;
    }

    /** Crit. 3: las ventas pendientes de facturar, para el supervisor. */
    @Transactional(readOnly = true)
    public List<FacturaRechazada> rechazadas() {
        return facturas.rechazadas();
    }

    /**
     * Crit. 2 y 3: deja la factura rechazada otra vez {@code PENDIENTE} con el DTE corregido. Debe ir en una transacción
     * y después del {@code COMMIT} se certifica con {@link #certificar}.
     *
     * @param receptor el receptor corregido, o {@code null} para conservar el de la factura
     * @param desdeCaja el cajero corrige su propia venta: solo un error del receptor y solo de su turno abierto
     */
    @Transactional
    public void reabrir(long ventaId, Receptor receptor, boolean desdeCaja, SesionActiva sesion) {
        try {
            Factura factura = facturas.deLaVenta(ventaId).orElseThrow(() -> new NegocioException(
                    "VENTA_SIN_FACTURA", "La venta no tiene factura."));
            FacturaRechazada rechazo = factura.estado() == EstadoFactura.RECHAZADA
                    ? facturas.rechazoDe(factura.id()).orElse(null)
                    : null;
            if (rechazo == null) {
                throw new NegocioException("FACTURA_NO_RECHAZADA", "La factura de la venta no está rechazada.");
            }
            Venta venta = ventas.porId(ventaId);
            if (desdeCaja) {
                if (!rechazo.motivo().corregibleEnCaja()) {
                    throw new NegocioException("RECHAZO_NO_CORREGIBLE_EN_CAJA", rechazo.motivo().mensaje());
                }
                if (turnos.exigirTurnoAbierto(sesion).id() != venta.turnoId()) {
                    throw new NegocioException("VENTA_DE_OTRO_TURNO",
                            "Solo se corrigen en caja las facturas de su turno: avise a su supervisor.");
                }
            }
            Receptor corregido = receptor != null ? receptor : facturas.imprimible(ventaId).orElseThrow().receptor();
            DatosDte datos = generacion.datos(venta, corregido, factura.emitidaEn());
            if (!facturas.reabrir(factura.id(), datos, GeneradorDte.generar(datos))) {
                throw new NegocioException("FACTURA_NO_RECHAZADA", "La factura de la venta ya no está rechazada.");
            }
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.FACTURA_RECHAZADA_CERTIFICADOR, false,
                    "Reenvío de factura rechazado: " + error.getMessage(), "venta", ventaId, null));
            throw error;
        }
    }

    /** Certifica la factura ya reabierta, después del {@code COMMIT}. */
    public FacturaDeVenta certificar(long ventaId, SesionActiva sesion) {
        return emision.reintentar(ventaId, sesion);
    }

    /** Crit. 3: el supervisor reenvía la factura con su mismo receptor, cuando ya se corrigió la causa. */
    public FacturaDeVenta reenviar(long ventaId, SesionActiva sesion) {
        transacciones.executeWithoutResult(transaccion -> reabrir(ventaId, null, false, sesion));
        return certificar(ventaId, sesion);
    }
}
