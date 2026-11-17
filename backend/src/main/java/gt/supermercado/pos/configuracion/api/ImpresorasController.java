package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras;
import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras.SolicitudImpresora;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-12.08 Configurar las impresoras. Solo con el permiso de modificar la configuración (el Administrador). La página
 * de prueba (crit. 2) está en {@code impresion.api}.
 */
@RestController
@RequestMapping("/api/configuracion/impresoras")
public class ImpresorasController {

    private final GestionImpresoras gestion;

    public ImpresorasController(GestionImpresoras gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public List<Impresora> listar() {
        return gestion.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Impresora crear(@RequestBody SolicitudImpresora solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.crear(solicitud, sesion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Impresora editar(@PathVariable long id, @RequestBody SolicitudImpresora solicitud,
                            @AuthenticationPrincipal SesionActiva sesion) {
        return gestion.editar(id, solicitud, sesion);
    }
}
