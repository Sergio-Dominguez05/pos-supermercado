package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionEstablecimiento;
import gt.supermercado.pos.configuracion.aplicacion.GestionEstablecimiento.SolicitudEstablecimiento;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * HU-12.02: el establecimiento SAT de la sucursal de V1. Con EP-13 la ruta llevará la sucursal. Solo para quien puede
 * modificar la configuración (HU-12.15 crit. 3).
 */
@RestController
@RequestMapping("/api/configuracion/establecimiento")
public class EstablecimientoController {

    private final GestionEstablecimiento gestion;

    public EstablecimientoController(GestionEstablecimiento gestion) {
        this.gestion = gestion;
    }

    public record RespuestaEstablecimiento(boolean configurado, String codigoSat, String nombre, String direccion,
                                           OffsetDateTime modificadoEn) {

        static RespuestaEstablecimiento de(Establecimiento establecimiento) {
            return new RespuestaEstablecimiento(true, establecimiento.codigoSat(), establecimiento.nombre(),
                    establecimiento.direccion(), establecimiento.modificadoEn());
        }

        static RespuestaEstablecimiento sinConfigurar() {
            return new RespuestaEstablecimiento(false, null, null, null, null);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaEstablecimiento obtener() {
        return gestion.deLaSucursal(gestion.sucursalPrincipal()).map(RespuestaEstablecimiento::de)
                .orElseGet(RespuestaEstablecimiento::sinConfigurar);
    }

    /** Guarda los datos. 422 si el código no es un número SAT válido o ya es de otra sucursal. */
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaEstablecimiento guardar(@RequestBody SolicitudEstablecimiento solicitud,
                                            @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaEstablecimiento.de(gestion.guardar(gestion.sucursalPrincipal(), solicitud, sesion));
    }
}
