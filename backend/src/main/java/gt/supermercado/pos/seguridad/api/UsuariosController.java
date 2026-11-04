package gt.supermercado.pos.seguridad.api;

import gt.supermercado.pos.seguridad.aplicacion.ConsultaUsuarios;
import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios;
import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios.NuevoUsuario;
import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios.UsuarioCreado;
import gt.supermercado.pos.seguridad.aplicacion.EdicionUsuarios;
import gt.supermercado.pos.seguridad.aplicacion.EdicionUsuarios.CambiosUsuario;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    private final CreacionUsuarios creacionUsuarios;
    private final ConsultaUsuarios consultaUsuarios;
    private final EdicionUsuarios edicionUsuarios;

    public UsuariosController(CreacionUsuarios creacionUsuarios, ConsultaUsuarios consultaUsuarios,
                              EdicionUsuarios edicionUsuarios) {
        this.creacionUsuarios = creacionUsuarios;
        this.consultaUsuarios = consultaUsuarios;
        this.edicionUsuarios = edicionUsuarios;
    }

    /** HU-01.06: lista con búsqueda por nombre o usuario ({@code q}) y filtros por rol y estado. */
    @GetMapping
    @PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
    public List<ResumenUsuario> listar(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Rol rol,
                                       @RequestParam(required = false) Boolean activo) {
        return consultaUsuarios.listar(q, rol, activo);
    }

    /**
     * Las reglas de formato del usuario y de la credencial se validan en {@link CreacionUsuarios}
     * para que los intentos rechazados queden en la bitácora; aquí solo se exige que vengan los datos.
     */
    public record SolicitudCrearUsuario(
            @NotBlank @Size(max = 100) String nombreCompleto,
            @NotBlank @Size(max = 100) String username,
            @NotNull @Pattern(regexp = "ADMIN|SUPERVISOR|CAJERO") String rol,
            @NotBlank @Size(max = 128) String credencialTemporal) {}

    /** HU-01.05: solo quien tiene el permiso de administrar usuarios (el Administrador). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
    public UsuarioCreado crear(@Valid @RequestBody SolicitudCrearUsuario solicitud,
                               @AuthenticationPrincipal SesionActiva actor) {
        return creacionUsuarios.crear(new NuevoUsuario(solicitud.nombreCompleto(), solicitud.username(),
                Rol.valueOf(solicitud.rol()), solicitud.credencialTemporal()), actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
    public ResumenUsuario obtener(@PathVariable long id) {
        return edicionUsuarios.obtener(id);
    }

    /**
     * Sin nombre de usuario: no se puede modificar después de creado (HU-01.07 crit. 2).
     * La credencial temporal solo hace falta al pasar entre Cajero y Administrador o Supervisor (crit. 3).
     */
    public record SolicitudEditarUsuario(
            @NotBlank @Size(max = 100) String nombreCompleto,
            @NotNull @Pattern(regexp = "ADMIN|SUPERVISOR|CAJERO") String rol,
            @Size(max = 128) String credencialTemporal) {}

    /** HU-01.07: cambia nombre completo y rol. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
    public ResumenUsuario editar(@PathVariable long id, @Valid @RequestBody SolicitudEditarUsuario solicitud,
                                 @AuthenticationPrincipal SesionActiva actor) {
        return edicionUsuarios.editar(id, new CambiosUsuario(solicitud.nombreCompleto(), Rol.valueOf(solicitud.rol()),
                solicitud.credencialTemporal()), actor);
    }
}
