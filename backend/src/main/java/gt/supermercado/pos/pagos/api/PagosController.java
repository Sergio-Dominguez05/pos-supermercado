package gt.supermercado.pos.pagos.api;

import gt.supermercado.pos.pagos.aplicacion.GestionPagos;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos.EstadoCobro;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos.SolicitudPago;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * EP-07 Pagos de la venta en curso del turno (HU-07.03). Como en ventas, la ruta usa {@code /actual}: nadie registra
 * un pago en la venta de otro turno cambiando un número en la URL.
 */
@RestController
@RequestMapping("/api/ventas/actual/pagos")
public class PagosController {

    private final GestionPagos gestionPagos;

    public PagosController(GestionPagos gestionPagos) {
        this.gestionPagos = gestionPagos;
    }

    /** Un pago tal como se muestra: sin el turno ni el usuario, y sin número de tarjeta (nunca se guarda). */
    public record RespuestaPago(long id, TipoTarjeta tipoTarjeta, String autorizacion, String ultimos4,
                                BigDecimal monto, OffsetDateTime registradoEn) {

        static RespuestaPago de(Pago pago) {
            return new RespuestaPago(pago.id(), pago.tipoTarjeta(), pago.autorizacion(), pago.ultimos4(), pago.monto(),
                    pago.registradoEn());
        }
    }

    /**
     * Crit. 1 y 2: el total a cobrar, lo pagado y lo pendiente, que se propone como monto. HU-07.07: {@code completo}
     * indica si los pagos cubren el total y, por tanto, si se puede finalizar.
     */
    public record RespuestaCobro(long ventaId, long numero, BigDecimal total, BigDecimal pagado, BigDecimal pendiente,
                                 boolean completo, List<RespuestaPago> pagos) {

        static RespuestaCobro de(EstadoCobro estado) {
            return new RespuestaCobro(estado.venta().id(), estado.venta().numero(), estado.venta().totales().total(),
                    estado.pagado(), estado.pendiente(), estado.completo(),
                    estado.pagos().stream().map(RespuestaPago::de).toList());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR')")
    public RespuestaCobro estado(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaCobro.de(gestionPagos.estado(sesion));
    }

    /**
     * HU-07.03: registra el pago con tarjeta aprobado por la terminal bancaria y devuelve el estado del cobro. 422 con
     * el motivo si falta el tipo o la autorización, si los últimos 4 dígitos no son 4 dígitos, si el monto supera el
     * pendiente o si la venta ya tiene su pago.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR')")
    public RespuestaCobro registrar(@RequestBody SolicitudPago solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaCobro.de(gestionPagos.registrar(solicitud, sesion));
    }

    /**
     * HU-07.06: elimina un pago de la venta en curso y devuelve el cobro con el pendiente recalculado (crit. 2). 404
     * {@code PAGO_NO_ENCONTRADO} si no es un pago vigente de la venta en curso del turno (crit. 3).
     */
    @DeleteMapping("/{pagoId}")
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR')")
    public RespuestaCobro eliminar(@PathVariable long pagoId, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaCobro.de(gestionPagos.eliminar(pagoId, sesion));
    }
}
