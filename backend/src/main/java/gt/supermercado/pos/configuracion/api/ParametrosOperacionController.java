package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionParametrosOperacion;
import gt.supermercado.pos.configuracion.aplicacion.GestionParametrosOperacion.Configuracion;
import gt.supermercado.pos.configuracion.aplicacion.GestionParametrosOperacion.Solicitud;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-12.13: configuración administrativa de los parámetros de caja y venta. */
@RestController
@RequestMapping("/api/configuracion/parametros")
public class ParametrosOperacionController {

    private final GestionParametrosOperacion gestion;

    public ParametrosOperacionController(GestionParametrosOperacion gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Configuracion obtener() {
        return gestion.consultar();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Configuracion guardar(@RequestBody Solicitud solicitud,
                                 @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.guardar(solicitud, sesion);
    }
}
