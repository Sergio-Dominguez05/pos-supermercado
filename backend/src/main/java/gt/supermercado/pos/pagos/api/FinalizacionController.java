package gt.supermercado.pos.pagos.api;

import gt.supermercado.pos.pagos.aplicacion.GestionPagos;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos.VentaFinalizada;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.pagos.api.PagosController.RespuestaPago;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** HU-07.08 Finalizar el cobro de la venta en curso del turno (Architecture.md §6.2). */
@RestController
public class FinalizacionController {

    private final GestionPagos gestionPagos;

    public FinalizacionController(GestionPagos gestionPagos) {
        this.gestionPagos = gestionPagos;
    }

    /** La venta finalizada: lo que la pantalla muestra antes de pasar a la siguiente. */
    public record RespuestaVentaFinalizada(long ventaId, long numero, int articulos, BigDecimal total,
                                           BigDecimal pagado, List<RespuestaPago> pagos, FacturaDeVenta factura) {

        static RespuestaVentaFinalizada de(VentaFinalizada finalizada) {
            return new RespuestaVentaFinalizada(finalizada.venta().id(), finalizada.venta().numero(),
                    finalizada.venta().articulos(), finalizada.venta().totales().total(), finalizada.pagado(),
                    finalizada.pagos().stream().map(RespuestaPago::de).toList(), finalizada.factura());
        }
    }

    /**
     * Finaliza el cobro: la venta queda registrada con sus pagos y se certifica su factura (HU-08.03). 422
     * {@code PAGO_INCOMPLETO} si los pagos no cubren el total (HU-07.07), {@code SIN_VENTA_EN_CURSO} si no hay venta
     * en curso, y {@code EMISOR_NO_CONFIGURADO} o similar si la factura no se puede armar. Si algo falla, la venta
     * sigue en curso y se puede reintentar (crit. 4). La respuesta dice si la factura quedó certificada, en
     * contingencia o rechazada.
     */
    @PostMapping("/api/ventas/actual/finalizar")
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR')")
    public RespuestaVentaFinalizada finalizar(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVentaFinalizada.de(gestionPagos.finalizar(sesion));
    }
}
