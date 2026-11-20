package gt.supermercado.pos.impresion.api;

import gt.supermercado.pos.facturacion.dominio.FacturaEmitida;
import gt.supermercado.pos.impresion.aplicacion.ReimpresionFacturas;
import gt.supermercado.pos.pagos.dominio.ResultadoImpresion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** HU-08.12 Reimprimir una factura. Con el permiso de reimprimir (Cajero, Supervisor y Administrador). */
@RestController
public class ReimpresionController {

    private final ReimpresionFacturas reimpresion;

    public ReimpresionController(ReimpresionFacturas reimpresion) {
        this.reimpresion = reimpresion;
    }

    /** Crit. 2: las facturas que esta sesión puede reimprimir (el cajero, las de su turno). */
    @GetMapping("/api/facturas/reimprimibles")
    @PreAuthorize("hasAuthority('FACTURA_REIMPRIMIR')")
    public List<FacturaEmitida> reimprimibles(@RequestParam(required = false) String buscar,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                              @AuthenticationPrincipal SesionActiva sesion) {
        return reimpresion.reimprimibles(buscar, fecha, sesion);
    }

    /** Crit. 1: la última factura de la caja de este equipo (F9). */
    @PostMapping("/api/facturas/ultima/reimprimir")
    @PreAuthorize("hasAuthority('FACTURA_REIMPRIMIR')")
    public ResultadoImpresion reimprimirUltima(@AuthenticationPrincipal SesionActiva sesion) {
        return reimpresion.reimprimirUltima(sesion);
    }

    @PostMapping("/api/facturas/{facturaId}/reimprimir")
    @PreAuthorize("hasAuthority('FACTURA_REIMPRIMIR')")
    public ResultadoImpresion reimprimir(@PathVariable long facturaId, @AuthenticationPrincipal SesionActiva sesion) {
        return reimpresion.reimprimir(facturaId, sesion);
    }
}
