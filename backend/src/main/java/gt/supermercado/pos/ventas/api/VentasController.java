package gt.supermercado.pos.ventas.api;

import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** El código tal como lo envía el lector o lo escribe el cajero; el servidor lo normaliza (HU-03.06). */
    public record SolicitudLinea(@NotBlank @Size(max = 100) String codigo) {}

    /**
     * HU-06.02: agrega el producto escaneado a la venta en curso (la inicia si hace falta) y devuelve la venta con
     * los totales recalculados. 404 si el código no existe (HU-03.08), 422 si el producto está inactivo (HU-03.09).
     */
    @PostMapping("/actual/lineas")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public RespuestaVenta agregar(@Valid @RequestBody SolicitudLinea solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.agregarPorCodigo(solicitud.codigo(), sesion));
    }

    /** HU-06.14: cancela la venta en curso; devuelve la venta cancelada. Sin autorización de supervisor (crit. 2). */
    @PostMapping("/actual/cancelar")
    @PreAuthorize("hasAuthority('VENTA_SUSPENDER_CANCELAR')")
    public RespuestaVenta cancelar(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.cancelar(sesion));
    }
}
