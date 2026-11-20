package gt.supermercado.pos.caja.api;

import gt.supermercado.pos.caja.aplicacion.CierreDeCaja;
import gt.supermercado.pos.caja.aplicacion.CierreDeCaja.CierreHecho;
import gt.supermercado.pos.caja.aplicacion.CierreDeCaja.ComparacionCierre;
import gt.supermercado.pos.caja.aplicacion.CierreDeCaja.PreparacionCierre;
import gt.supermercado.pos.caja.aplicacion.CierreDeCaja.OpcionesCierres;
import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.ComprobanteDeCierre;
import gt.supermercado.pos.caja.dominio.FiltrosCierres;
import gt.supermercado.pos.caja.dominio.PaginaCierres;
import gt.supermercado.pos.caja.dominio.ImpresionDeCierre;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.caja.dominio.Turno;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HU-09.08 a HU-09.10: el cierre de la caja propia, sus comprobantes y los cierres anteriores; HU-09.12: el de otro. */
@RestController
public class CierreController {

    private final CierreDeCaja cierre;

    public CierreController(CierreDeCaja cierre) {
        this.cierre = cierre;
    }

    public record SolicitudCierre(BigDecimal totalLote, String justificacion) {}

    /** HU-09.08 y HU-09.11: prepara el cierre sin exponer el total cuando corresponde un cierre ciego. */
    @GetMapping("/api/turnos/actual/cierre")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public PreparacionCierre resumen(@AuthenticationPrincipal SesionActiva sesion) {
        return cierre.resumen(sesion);
    }

    /** HU-09.11 crit. 2: compara después de que el cajero confirma el monto ingresado. */
    @PostMapping("/api/turnos/actual/cierre/comparar")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public ComparacionCierre comparar(@RequestBody SolicitudCierre solicitud,
                                      @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.comparar(solicitud.totalLote(), sesion);
    }

    /** 422 con ventas por resolver, sin total de lote o con una diferencia sin justificar. */
    @PostMapping("/api/turnos/actual/cerrar")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public CierreHecho cerrar(@RequestBody SolicitudCierre solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.cerrar(solicitud.totalLote(), solicitud.justificacion(), sesion);
    }

    /** HU-09.10 crit. 3: el cajero ve sus cierres; el supervisor, todos. */
    @GetMapping("/api/turnos/cierres")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA') or hasAuthority('CAJA_VER_CIERRES')")
    public List<CierreDeTurno> cierres(@AuthenticationPrincipal SesionActiva sesion) {
        return cierre.cierresAnteriores(sesion);
    }

    /**
     * HU-09.12 crit. 1: los turnos abiertos de los demás usuarios, para cerrarlos. Es distinto de
     * {@code /api/turnos/abiertos} (HU-09.06), que solo consulta e incluye el turno propio.
     */
    @GetMapping("/api/turnos/por-cerrar")
    @PreAuthorize("hasAuthority('CAJA_CERRAR_OTRA')")
    public List<Turno> turnosAbiertos(@AuthenticationPrincipal SesionActiva sesion) {
        return cierre.turnosAbiertosDeOtros(sesion);
    }

    /** HU-09.12: lo cobrado en el turno de otro usuario, antes de cerrarlo; 422 si ya se cerró o es propio. */
    @GetMapping("/api/turnos/{turnoId}/cierre")
    @PreAuthorize("hasAuthority('CAJA_CERRAR_OTRA')")
    public ResumenDeTurno resumenDeOtro(@PathVariable long turnoId, @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.resumenDeOtro(turnoId, sesion);
    }

    /** HU-09.12 crit. 1 a 3: el supervisor cierra el turno de otro usuario con las mismas reglas del cierre propio. */
    @PostMapping("/api/turnos/{turnoId}/cerrar")
    @PreAuthorize("hasAuthority('CAJA_CERRAR_OTRA')")
    public CierreHecho cerrarDeOtro(@PathVariable long turnoId, @RequestBody SolicitudCierre solicitud,
                                    @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.cerrarDeOtro(turnoId, solicitud.totalLote(), solicitud.justificacion(), sesion);
    }

    /** HU-09.13 crit. 1 y 2: los cierres de todas las cajas con filtros opcionales; 422 si las fechas están invertidas. */
    @GetMapping("/api/cierres")
    @PreAuthorize("hasAuthority('CAJA_VER_CIERRES')")
    public PaginaCierres consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long cajeroId,
            @RequestParam(defaultValue = "false") boolean soloConDiferencias,
            @RequestParam(defaultValue = "0") int pagina) {
        return cierre.consultarCierres(new FiltrosCierres(desde, hasta, cajaId, cajeroId, soloConDiferencias, pagina));
    }

    @GetMapping("/api/cierres/opciones")
    @PreAuthorize("hasAuthority('CAJA_VER_CIERRES')")
    public OpcionesCierres opciones() {
        return cierre.opcionesCierres();
    }

    /** HU-09.13 crit. 3: el cierre completo con lo cobrado en el turno; 404 si no existe o no está cerrado. */
    @GetMapping("/api/cierres/{turnoId:\\d+}")
    @PreAuthorize("hasAuthority('CAJA_VER_CIERRES')")
    public ComprobanteDeCierre detalle(@PathVariable long turnoId) {
        return cierre.comprobante(turnoId);
    }

    @PostMapping("/api/turnos/{turnoId}/cierre/reimprimir")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA') or hasAuthority('CAJA_VER_CIERRES')")
    public ImpresionDeCierre.Resultado reimprimir(@PathVariable long turnoId,
                                                 @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.reimprimir(turnoId, sesion);
    }
}
