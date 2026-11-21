package gt.supermercado.pos.bitacora.api;

import gt.supermercado.pos.bitacora.aplicacion.ConservacionBitacora;
import gt.supermercado.pos.bitacora.aplicacion.ConservacionBitacora.EstadoConservacion;
import gt.supermercado.pos.bitacora.aplicacion.ConservacionBitacora.ResultadoArchivo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HU-11.10: la conservación de la bitácora es configuración, por eso va fuera de {@code /api/bitacora}, que sigue sin
 * ninguna operación de escritura (HU-11.05 crit. 1). Ninguna de estas rutas modifica ni borra registros: archivar los
 * mueve con su secuencia y su hash.
 */
@RestController
@RequestMapping("/api/configuracion/bitacora")
public class ConservacionBitacoraController {

    private final ConservacionBitacora conservacion;

    public ConservacionBitacoraController(ConservacionBitacora conservacion) {
        this.conservacion = conservacion;
    }

    public record SolicitudPlazo(Integer plazoAnios) {}

    public record SolicitudArchivo(LocalDate antesDe) {}

    /** El plazo de conservación, cuántos registros hay activos y archivados, y hasta qué día se puede archivar. */
    @GetMapping
    @PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
    public EstadoConservacion estado() {
        return conservacion.estado();
    }

    /** Crit. 1: cambia el plazo (4 a 50 años). Solo el Administrador; 422 fuera del rango. */
    @PutMapping
    @PreAuthorize("hasAuthority('ROL_ADMIN')")
    public EstadoConservacion cambiarPlazo(@RequestBody SolicitudPlazo solicitud,
                                           @AuthenticationPrincipal SesionActiva sesion) {
        return conservacion.cambiarPlazo(solicitud.plazoAnios(), sesion);
    }

    /**
     * Crit. 2: mueve al archivo los registros anteriores al día {@code antesDe} (de hace más de 90 días). Se siguen
     * consultando, exportando y verificando. Solo el Administrador; 422 con una fecha demasiado reciente.
     */
    @PostMapping("/archivo")
    @PreAuthorize("hasAuthority('ROL_ADMIN')")
    public ResultadoArchivo archivar(@RequestBody SolicitudArchivo solicitud,
                                     @AuthenticationPrincipal SesionActiva sesion) {
        return conservacion.archivar(solicitud.antesDe(), sesion);
    }
}
