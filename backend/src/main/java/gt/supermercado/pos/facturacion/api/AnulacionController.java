package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.AnulacionFacturas;
import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** HU-08.10 Anular una factura. Solo con el permiso de anular facturas (Supervisor y Administrador, crit. 5). */
@RestController
public class AnulacionController {

    private final AnulacionFacturas anulacion;

    public AnulacionController(AnulacionFacturas anulacion) {
        this.anulacion = anulacion;
    }

    public record SolicitudAnulacion(String motivo) {}

    /** Crit. 1: por número, serie o autorización ({@code buscar}) y por fecha de emisión ({@code fecha}). */
    @GetMapping("/api/facturas")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public List<FacturaEmitida> buscar(@RequestParam(required = false) String buscar,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return anulacion.buscar(buscar, fecha);
    }

    /** 422 si falta el motivo, si ya está anulada, si el turno de la venta se cerró o si el certificador la rechaza. */
    @PostMapping("/api/facturas/{facturaId}/anular")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public FacturaEmitida anular(@PathVariable long facturaId, @RequestBody SolicitudAnulacion solicitud,
                                 @AuthenticationPrincipal SesionActiva sesion) {
        return anulacion.anular(facturaId, solicitud.motivo(), sesion);
    }
}
