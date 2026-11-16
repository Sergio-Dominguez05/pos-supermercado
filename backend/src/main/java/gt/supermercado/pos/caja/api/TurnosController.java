package gt.supermercado.pos.caja.api;

import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.EstadoTurno;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** EP-09 Caja. HU-09.01: abrir el turno en la caja del equipo, con el permiso de operar la propia caja. */
@RestController
@RequestMapping("/api/turnos")
public class TurnosController {

    private final GestionTurnos gestionTurnos;

    public TurnosController(GestionTurnos gestionTurnos) {
        this.gestionTurnos = gestionTurnos;
    }

    /** El turno, más si es del usuario de la sesión o de otra persona. */
    public record RespuestaTurno(long id, long cajaId, String cajaNombre, String usuarioNombre, EstadoTurno estado,
                                 OffsetDateTime abiertoEn, boolean propio) {

        static RespuestaTurno de(Turno turno, SesionActiva sesion) {
            return new RespuestaTurno(turno.id(), turno.cajaId(), turno.cajaNombre(), turno.usuarioNombre(),
                    turno.estado(), turno.abiertoEn(), turno.esDe(sesion.usuarioId()));
        }
    }

    /** Turno abierto en la caja de esta sesión; 204 si la caja está cerrada o el equipo no tiene caja. */
    @GetMapping("/actual")
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public ResponseEntity<RespuestaTurno> actual(@AuthenticationPrincipal SesionActiva sesion) {
        return gestionTurnos.actualDeLaCaja(sesion).map(turno -> ResponseEntity.ok(RespuestaTurno.de(turno, sesion)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_OPERAR_PROPIA')")
    public RespuestaTurno abrir(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaTurno.de(gestionTurnos.abrir(sesion), sesion);
    }
}
