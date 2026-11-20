package gt.supermercado.pos.seguridad.api;

import gt.supermercado.pos.seguridad.aplicacion.ConfiguracionPermisos;
import gt.supermercado.pos.seguridad.dominio.MatrizPermisos;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** HU-01.10: la matriz de permisos por rol, que solo el Administrador configura. */
@RestController
public class PermisosController {

    private final ConfiguracionPermisos configuracion;

    public PermisosController(ConfiguracionPermisos configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping("/api/permisos")
    @PreAuthorize("hasAuthority('PERMISO_CONFIGURAR')")
    public MatrizPermisos matriz() {
        return configuracion.matriz();
    }

    public record SolicitudPermisos(Set<String> permisos) {}

    /** Deja al rol con exactamente esos permisos; 422 para el Administrador o con un permiso exclusivo. */
    @PutMapping("/api/roles/{rol}/permisos")
    @PreAuthorize("hasAuthority('PERMISO_CONFIGURAR')")
    public MatrizPermisos cambiar(@PathVariable Rol rol, @RequestBody SolicitudPermisos solicitud,
                                  @AuthenticationPrincipal SesionActiva sesion) {
        return configuracion.cambiar(rol, solicitud.permisos(), sesion);
    }
}
