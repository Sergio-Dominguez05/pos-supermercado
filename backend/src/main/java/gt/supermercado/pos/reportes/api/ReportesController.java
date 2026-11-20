package gt.supermercado.pos.reportes.api;

import gt.supermercado.pos.reportes.aplicacion.GeneracionReportes;
import gt.supermercado.pos.reportes.dominio.Agrupacion;
import gt.supermercado.pos.reportes.dominio.CodigoReporte;
import gt.supermercado.pos.reportes.dominio.FormatoExportacion;
import gt.supermercado.pos.reportes.dominio.PanelDelDia;
import gt.supermercado.pos.reportes.dominio.ParametrosReporte;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import gt.supermercado.pos.reportes.dominio.DetalleFacturasIva;
import gt.supermercado.pos.reportes.dominio.DetallePagosTarjeta;
import gt.supermercado.pos.reportes.dominio.DetalleVentasCajero;
import gt.supermercado.pos.reportes.dominio.ReporteAnuladas;
import gt.supermercado.pos.reportes.dominio.ReporteCierres;
import gt.supermercado.pos.reportes.dominio.ReporteIva;
import gt.supermercado.pos.reportes.dominio.ReporteTarjetas;
import gt.supermercado.pos.reportes.dominio.TipoTarjetaReporte;
import gt.supermercado.pos.reportes.dominio.OrdenProductos;
import gt.supermercado.pos.reportes.dominio.ReporteProductosMasVendidos;
import gt.supermercado.pos.reportes.dominio.ReporteVentasPorCaja;
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

    /** HU-10.12: el panel resumen del día, con sus alertas; la pantalla lo vuelve a pedir cada minuto. */
    @GetMapping("/api/reportes/panel")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public PanelDelDia panel() {
        return reportes.panel();
    }

    /** HU-10.04: por caja, ventas, total vendido, turnos y participación, para compararlas en el mismo período. */
    @GetMapping("/api/reportes/cajas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteVentasPorCaja ventasPorCaja(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.ventasPorCaja(desde, hasta, cajaId, cajeroId, sesion);
    }

    /**
     * HU-10.05: los productos más vendidos; {@code ordenarPor} es {@code CANTIDAD} (por omisión) o {@code MONTO} y
     * {@code limite} cuántos mostrar (20 por omisión, entre 1 y 500; 422 fuera de ese rango).
     */
    @GetMapping("/api/reportes/productos")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteProductosMasVendidos productosMasVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) OrdenProductos ordenarPor,
            @RequestParam(required = false) Integer limite,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.productosMasVendidos(desde, hasta, cajaId, cajeroId, ordenarPor, limite, sesion);
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

    /** HU-10.06 crit. 1 y 2: débito y crédito, con los pagos de facturas anuladas aparte. */
    @GetMapping("/api/reportes/tarjetas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteTarjetas ingresosPorTarjeta(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) TipoTarjetaReporte tipoTarjeta,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.ingresosPorTarjeta(desde, hasta, cajaId, cajeroId, tipoTarjeta, sesion);
    }

    /** HU-10.06 crit. 3: las transacciones de un tipo de tarjeta ({@code DEBITO} o {@code CREDITO}). */
    @GetMapping("/api/reportes/tarjetas/{tipo}/pagos")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public DetallePagosTarjeta detalleDeTarjeta(
            @PathVariable TipoTarjetaReporte tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.detalleDeTarjeta(tipo, desde, hasta, cajaId, cajeroId, sesion);
    }

    /** HU-10.07: el IVA de las facturas certificadas, por día. */
    @GetMapping("/api/reportes/iva")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteIva ivaCobrado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.ivaCobrado(desde, hasta, cajaId, cajeroId, sesion);
    }

    /** HU-10.07 crit. 3: el detalle por factura. */
    @GetMapping("/api/reportes/iva/facturas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public DetalleFacturasIva facturasDelIva(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.facturasDelIva(desde, hasta, cajaId, cajeroId, sesion);
    }

    /**
     * HU-10.13 y HU-10.14: cualquier reporte o detalle ({@link CodigoReporte}) como PDF o Excel, con los mismos filtros
     * que en pantalla. 422 con filtros inválidos (en JSON, como los demás errores).
     */
    @GetMapping("/api/reportes/exportar/{codigo}")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ResponseEntity<byte[]> exportar(
            @PathVariable CodigoReporte codigo,
            @RequestParam FormatoExportacion formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) TipoTarjetaReporte tipoTarjeta,
            @RequestParam(required = false) Agrupacion agrupacion,
            @RequestParam(defaultValue = "false") boolean soloConDiferencias,
            @RequestParam(required = false) Long anuladaPorId,
            @RequestParam(required = false) OrdenProductos ordenarPor,
            @RequestParam(required = false) Integer limite,
            @AuthenticationPrincipal SesionActiva sesion) {
        ParametrosReporte parametros = new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipoTarjeta, agrupacion,
                soloConDiferencias, anuladaPorId, ordenarPor, limite);
        GeneracionReportes.ArchivoReporte archivo = reportes.exportar(codigo, parametros, formato, sesion);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }

    /** HU-10.08: los cierres de caja del período; {@code soloConDiferencias=true} deja solo los que tienen diferencia. */
    @GetMapping("/api/reportes/cierres")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteCierres cierresDeCaja(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(defaultValue = "false") boolean soloConDiferencias,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.cierresDeCaja(desde, hasta, cajaId, cajeroId, soloConDiferencias, sesion);
    }

    /** HU-10.09: las facturas anuladas en el período (por fecha de anulación). */
    @GetMapping("/api/reportes/anuladas")
    @PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
    public ReporteAnuladas facturasAnuladas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(required = false) Long anuladaPorId,
            @AuthenticationPrincipal SesionActiva sesion) {
        return reportes.facturasAnuladas(desde, hasta, cajaId, cajeroId, anuladaPorId, sesion);
    }
}
