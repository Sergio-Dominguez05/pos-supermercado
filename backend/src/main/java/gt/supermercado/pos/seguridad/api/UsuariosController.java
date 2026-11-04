package gt.supermercado.pos.seguridad.api;

import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios;
import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios.NuevoUsuario;
import gt.supermercado.pos.seguridad.aplicacion.CreacionUsuarios.UsuarioCreado;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuariosController {

    private final CreacionUsuarios creacionUsuarios;

    public UsuariosController(CreacionUsuarios creacionUsuarios) {
        this.creacionUsuarios = creacionUsuarios;
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
}
