package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.ConsultaHistorialFacturas;
import gt.supermercado.pos.facturacion.dominio.DetalleFactura;
import gt.supermercado.pos.facturacion.dominio.EstadoFactura;
import gt.supermercado.pos.facturacion.dominio.FiltrosHistorial;
import gt.supermercado.pos.facturacion.dominio.HistorialFacturas;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HU-08.13 y HU-08.14: el historial y el detalle de las facturas, para el Supervisor y el Administrador (permiso de
 * anular facturas, decisión de Sergio). Reimprimir y anular desde el detalle usan sus endpoints de siempre.
 */
@RestController
public class HistorialFacturasController {

    private final ConsultaHistorialFacturas consulta;

    public HistorialFacturasController(ConsultaHistorialFacturas consulta) {
        this.consulta = consulta;
    }

    /** Crit. 2 y 3: todos los filtros son opcionales; 422 si las fechas están invertidas. */
    @GetMapping("/api/facturas/historial")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public HistorialFacturas historial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoFactura estado,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int pagina) {
        return consulta.buscar(new FiltrosHistorial(desde, hasta, estado, cajaId, cajeroId, cliente, buscar, pagina));
    }

    @GetMapping("/api/facturas/historial/opciones")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public ConsultaHistorialFacturas.OpcionesHistorial opciones() {
        return consulta.opciones();
    }

    /** HU-08.14: 404 si la factura no existe. */
    @GetMapping("/api/facturas/{facturaId:\\d+}")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public DetalleFactura detalle(@PathVariable long facturaId) {
        return consulta.detalle(facturaId);
    }
}
