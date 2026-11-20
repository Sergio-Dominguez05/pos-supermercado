package gt.supermercado.pos.reportes.api;

import gt.supermercado.pos.reportes.aplicacion.GeneracionReportes;
import gt.supermercado.pos.reportes.dominio.Agrupacion;
import gt.supermercado.pos.reportes.dominio.DetalleVentasCajero;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCajero;
import gt.supermercado.pos.reportes.dominio.OpcionesDeFiltro;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPeriodo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * EP-10 Reportes. Todos exigen {@code REPORTE_CONSULTAR}: el Administrador y el Supervisor; los cajeros no
 * (HU-10.15 crit. 1 y 2). Cada generación queda en la bitácora (crit. 3).
 */
@RestController
public class ReportesController {

    private final GeneracionReportes reportes;

    public ReportesController(GeneracionReportes reportes) {
        this.reportes = reportes;
    }

    /** HU-10.01: cajas, cajeros, tipos de tarjeta, agrupaciones y rangos rápidos (crit. 3). */
    @GetMapping("/api/reportes/filtros")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public OpcionesDeFiltro filtros() {
        return reportes.opciones();
    }

    /** HU-10.02: fechas obligatorias ({@code yyyy-MM-dd}, ambas incluidas); 422 si faltan o están invertidas. */
    @GetMapping("/api/reportes/ventas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteVentasPeriodo ventasPorPeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) Agrupacion agrupacion,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.ventasPorPeriodo(desde, hasta, cajaId, cajeroId, agrupacion, sesion);
    }

    /** HU-10.03 crit. 1: por cajero, con las ventas canceladas, las líneas eliminadas y las facturas anuladas. */
    @GetMapping("/api/reportes/cajeros")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteVentasPorCajero ventasPorCajero(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.ventasPorCajero(desde, hasta, cajaId, cajeroId, sesion);
    }

    /** HU-10.03 crit. 2: el detalle de las ventas de un cajero en el período. */
    @GetMapping("/api/reportes/cajeros/{cajeroId}/ventas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public DetalleVentasCajero detalleDeCajero(
            @PathVariable long cajeroId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.detalleDeCajero(cajeroId, desde, hasta, cajaId, sesion);
    }
}
