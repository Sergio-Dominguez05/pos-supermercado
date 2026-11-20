package gt.supermercado.pos.caja.api;

import gt.supermercado.pos.caja.aplicacion.CierreDeCaja;
import gt.supermercado.pos.caja.aplicacion.CierreDeCaja.CierreHecho;
import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.ImpresionDeCierre;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** HU-09.08 a HU-09.10: el cierre de la caja propia, sus comprobantes y los cierres anteriores. */
@RestController
public class CierreController {

    private final CierreDeCaja cierre;

    public CierreController(CierreDeCaja cierre) {
        this.cierre = cierre;
    }

    public record SolicitudCierre(BigDecimal totalLote, String justificacion) {}

    /** HU-09.08 crit. 3: lo cobrado en el turno abierto, para comparar con el cierre de lote. */
    @GetMapping("/api/turnos/actual/cierre")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public ResumenDeTurno resumen(@AuthenticationPrincipal SesionActiva sesion) {
        return cierre.resumen(sesion);
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

    @PostMapping("/api/turnos/{turnoId}/cierre/reimprimir")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA') or hasAuthority('CAJA_VER_CIERRES')")
    public ImpresionDeCierre.Resultado reimprimir(@PathVariable long turnoId,
                                                 @AuthenticationPrincipal SesionActiva sesion) {
        return cierre.reimprimir(turnoId, sesion);
    }
}
