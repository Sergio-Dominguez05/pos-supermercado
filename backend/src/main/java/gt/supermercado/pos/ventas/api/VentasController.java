package gt.supermercado.pos.ventas.api;

import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * EP-06 Ventas. El cajero trabaja siempre sobre la venta en curso de su turno, así que las rutas usan
 * {@code /actual} en lugar del id: nadie puede operar la venta de otro turno cambiando un número en la URL.
 */
@RestController
@RequestMapping("/api/ventas")
public class VentasController {

    private final GestionVentas gestionVentas;

    public VentasController(GestionVentas gestionVentas) {
        this.gestionVentas = gestionVentas;
    }

    /** La venta con sus totales ya calculados por el servidor: la pantalla nunca calcula montos (§6.1). */
    public record RespuestaVenta(long id, long numero, EstadoVenta estado, String cajaNombre, OffsetDateTime creadaEn,
                                 List<LineaVenta> lineas, int articulos, BigDecimal subtotal, BigDecimal iva,
                                 BigDecimal total) {

        static RespuestaVenta de(Venta venta) {
            var totales = venta.totales();
            return new RespuestaVenta(venta.id(), venta.numero(), venta.estado(), venta.cajaNombre(), venta.creadaEn(),
                    venta.lineas(), venta.articulos(), totales.subtotal(), totales.iva(), totales.total());
        }
    }

    /** La venta en curso del turno; 204 si todavía no hay una. */
    @GetMapping("/actual")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public ResponseEntity<RespuestaVenta> actual(@AuthenticationPrincipal SesionActiva sesion) {
        return gestionVentas.actual(sesion).map(venta -> ResponseEntity.ok(RespuestaVenta.de(venta)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** HU-06.01: inicia la venta, o devuelve la que ya está en curso en el turno. */
    @PostMapping
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public RespuestaVenta iniciar(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.iniciar(sesion));
    }
}
