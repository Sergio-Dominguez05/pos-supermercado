package gt.supermercado.pos.seguridad.api;

import gt.supermercado.pos.seguridad.aplicacion.InicioSesion;
import gt.supermercado.pos.seguridad.aplicacion.InicioSesion.SesionIniciada;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.infraestructura.CookieSesion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final InicioSesion inicioSesion;
    private final CookieSesion cookie;

    public AutenticacionController(InicioSesion inicioSesion, CookieSesion cookie) {
        this.inicioSesion = inicioSesion;
        this.cookie = cookie;
    }

    /** Los límites de tamaño evitan calcular Argon2 sobre textos enormes. */
    public record SolicitudLogin(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 128) String contrasena) {}

    /** HU-01.02 crit. 2: el PIN es numérico, de 4 a 6 dígitos. */
    public record SolicitudLoginPin(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Pattern(regexp = "\\d{4,6}", message = "debe tener de 4 a 6 dígitos") String pin) {}

    public record UsuarioSesion(String username, String nombreCompleto, String rol, String rolNombre,
                                boolean credencialTemporal, List<String> permisos) {

        static UsuarioSesion de(SesionActiva sesion) {
            return new UsuarioSesion(sesion.username(), sesion.nombreCompleto(), sesion.rol().name(),
                    sesion.rolNombre(), sesion.credencialTemporal(), sesion.permisos().stream().sorted().toList());
        }
    }

    /** HU-01.01: el token viaja solo en la cookie HttpOnly; la respuesta trae los datos y permisos del usuario. */
    @PostMapping("/login")
    public ResponseEntity<UsuarioSesion> login(@Valid @RequestBody SolicitudLogin solicitud) {
        return respuesta(inicioSesion.conContrasena(solicitud.username(), solicitud.contrasena()));
    }

    /** HU-01.02: inicio de sesión de cajeros con PIN. */
    @PostMapping("/login-pin")
    public ResponseEntity<UsuarioSesion> loginPin(@Valid @RequestBody SolicitudLoginPin solicitud) {
        return respuesta(inicioSesion.conPin(solicitud.username(), solicitud.pin()));
    }

    /** Usuario de la sesión actual; el frontend lo usa al recargar la página. */
    @GetMapping("/sesion")
    public UsuarioSesion sesion(@AuthenticationPrincipal SesionActiva sesion) {
        return UsuarioSesion.de(sesion);
    }

    private ResponseEntity<UsuarioSesion> respuesta(SesionIniciada iniciada) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.crear(iniciada.token()).toString())
                .body(UsuarioSesion.de(iniciada.sesion()));
    }
}
