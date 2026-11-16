package gt.supermercado.pos.ventas.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.impuestos.dominio.TasaIva;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Venta en curso del cajero (EP-06). Toda venta pertenece al turno propio abierto en la caja de la sesión
 * (HU-09.02 crit. 2): sin turno, se rechaza con {@code CAJA_CERRADA} (HU-06.01 crit. 2).
 */
@Service
public class GestionVentas {

    private final RepositorioVentas ventas;
    private final GestionTurnos turnos;
    private final Bitacora bitacora;

    public GestionVentas(RepositorioVentas ventas, GestionTurnos turnos, Bitacora bitacora) {
        this.ventas = ventas;
        this.turnos = turnos;
        this.bitacora = bitacora;
    }

    /** La venta en curso del turno del cajero, si tiene una. */
    @Transactional(readOnly = true)
    public Optional<Venta> actual(SesionActiva sesion) {
        return ventas.enCursoDelTurno(turnos.exigirTurnoAbierto(sesion).id());
    }

    /**
     * HU-06.01: deja la caja lista para recibir productos (crit. 1). Si ya hay una venta en curso en el turno, la
     * devuelve; si no, crea una con un número interno único (crit. 4) y la tasa de IVA vigente (HU-04.01 crit. 3).
     */
    @Transactional
    public Venta iniciar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        long id = ventas.iniciar(turno.id(), turno.cajaId(), sesion.usuarioId(), TasaIva.VIGENTE.valor());
        return ventas.buscarPorId(id).orElseThrow();
    }

    /**
     * HU-06.14: cancela la venta en curso del turno. No requiere autorización de un supervisor (crit. 2): basta el
     * permiso del cajero. La venta queda {@code CANCELADA}, así que no genera factura ni cuenta para la caja
     * (crit. 3), y la cancelación queda en la bitácora con el detalle de los productos (crit. 4). La confirmación la
     * pide la pantalla (crit. 1).
     */
    @Transactional
    public Venta cancelar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        Venta venta = ventas.enCursoDelTurno(turno.id())
                .orElseThrow(() -> rechazar(sesion, "SIN_VENTA_EN_CURSO", "No hay ninguna venta en curso para cancelar."));
        if (!ventas.cancelar(venta.id())) {
            throw rechazar(sesion, "SIN_VENTA_EN_CURSO", "La venta ya no está en curso.");
        }
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.VENTA_CANCELADA,
                true, "Venta N.º %d cancelada: %d artículos, total Q%s".formatted(venta.numero(), venta.articulos(),
                        venta.totales().total()), "venta", venta.id(), detalle(venta)));
        return ventas.buscarPorId(venta.id()).orElseThrow();
    }

    /** Los productos de la venta cancelada, tal como estaban: código, descripción, cantidad y subtotal. */
    private static Map<String, Object> detalle(Venta venta) {
        List<Map<String, Object>> productos = venta.lineas().stream().map(GestionVentas::producto).toList();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("productos", productos);
        detalle.put("total", venta.totales().total());
        return detalle;
    }

    private static Map<String, Object> producto(LineaVenta linea) {
        Map<String, Object> producto = new LinkedHashMap<>();
        producto.put("codigo", linea.codigoBarras());
        producto.put("descripcion", linea.descripcion());
        producto.put("cantidad", linea.cantidad());
        producto.put("precioUnitario", linea.precioUnitario());
        producto.put("subtotal", linea.subtotalConIva());
        return producto;
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva sesion, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.VENTA_CANCELADA, false, "Cancelación de venta rechazada: " + mensaje, null, null, null));
        return new NegocioException(codigo, mensaje);
    }
}
