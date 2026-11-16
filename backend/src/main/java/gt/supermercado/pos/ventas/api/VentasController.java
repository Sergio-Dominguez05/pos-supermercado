package gt.supermercado.pos.ventas.api;

import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /**
     * El producto a agregar: o el código tal como lo envía el lector o lo escribe el cajero (HU-06.02, HU-06.03
     * crit. 1; el servidor lo normaliza), o el id del producto elegido en la búsqueda por descripción (HU-06.03
     * crit. 2). Exactamente uno de los dos.
     */
    public record SolicitudLinea(@Size(max = 100) String codigo, Long productoId, Integer cantidad,
                                 Boolean confirmada) {

        @AssertTrue(message = "indique el código o el producto, pero no ambos")
        public boolean isUnaReferencia() {
            return (codigo != null && !codigo.isBlank()) != (productoId != null);
        }

        /** HU-06.04: sin cantidad se agrega una unidad (HU-06.02 crit. 1); sin {@code confirmada}, no se confirmó. */
        int unidades() {
            return cantidad == null ? 1 : cantidad;
        }

        boolean confirmo() {
            return Boolean.TRUE.equals(confirmada);
        }
    }

    /**
     * HU-06.02, HU-06.03 y HU-06.04: agrega el producto a la venta en curso (la inicia si hace falta) y devuelve la
     * venta con los totales recalculados. 404 si el código o el producto no existen (HU-03.08), 422 si el producto
     * está inactivo (HU-03.09), si la cantidad no es válida ({@code CANTIDAD_INVALIDA}) o si hay que confirmarla
     * ({@code CANTIDAD_POR_CONFIRMAR}: se reenvía con {@code confirmada}).
     */
    @PostMapping("/actual/lineas")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public RespuestaVenta agregar(@Valid @RequestBody SolicitudLinea solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(solicitud.productoId() != null
                ? gestionVentas.agregarProducto(solicitud.productoId(), solicitud.unidades(), solicitud.confirmo(), sesion)
                : gestionVentas.agregarPorCodigo(solicitud.codigo(), solicitud.unidades(), solicitud.confirmo(), sesion));
    }

    /** La cantidad nueva de la línea (HU-06.04 crit. 2) y si el cajero ya confirmó una cantidad alta (crit. 4). */
    public record SolicitudCantidad(@NotNull Integer cantidad, Boolean confirmada) {

        boolean confirmo() {
            return Boolean.TRUE.equals(confirmada);
        }
    }

    /**
     * HU-06.04 crit. 2: cambia la cantidad de una línea de la venta en curso y devuelve la venta recalculada. 404 si
     * la línea no es de la venta en curso del turno; 422 si la cantidad no es válida o hay que confirmarla.
     */
    @PutMapping("/actual/lineas/{lineaId}/cantidad")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public RespuestaVenta cambiarCantidad(@PathVariable long lineaId, @Valid @RequestBody SolicitudCantidad solicitud,
                                          @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.cambiarCantidad(lineaId, solicitud.cantidad(), solicitud.confirmo(), sesion));
    }

    /**
     * HU-06.07: quita la línea de la venta en curso y devuelve la venta con los totales recalculados (crit. 4). Sin
     * autorización de un supervisor (crit. 2). 404 si la línea no es de la venta en curso del turno.
     */
    @DeleteMapping("/actual/lineas/{lineaId}")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public RespuestaVenta eliminarLinea(@PathVariable long lineaId, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.eliminarLinea(lineaId, sesion));
    }

    /** HU-06.14: cancela la venta en curso; devuelve la venta cancelada. Sin autorización de supervisor (crit. 2). */
    @PostMapping("/actual/cancelar")
    @PreAuthorize("hasAuthority('VENTA_SUSPENDER_CANCELAR')")
    public RespuestaVenta cancelar(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaVenta.de(gestionVentas.cancelar(sesion));
    }
}
