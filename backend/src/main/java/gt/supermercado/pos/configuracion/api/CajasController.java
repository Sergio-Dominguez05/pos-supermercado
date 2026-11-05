package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionCajas;
import gt.supermercado.pos.configuracion.aplicacion.GestionCajas.EquipoVinculado;
import gt.supermercado.pos.configuracion.dominio.Caja;
import gt.supermercado.pos.seguridad.dominio.IdentificadorEquipo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** HU-12.05 Registrar las cajas. Solo con el permiso de modificar la configuración (el Administrador). */
@RestController
@RequestMapping("/api/cajas")
public class CajasController {

    /** El equipo de una caja casi nunca cambia: la cookie dura años y se renueva al volver a vincularlo. */
    private static final Duration DURACION_COOKIE_EQUIPO = Duration.ofDays(5 * 365);

    private final GestionCajas gestionCajas;
    private final boolean cookieSegura;

    public CajasController(GestionCajas gestionCajas, @Value("${pos.seguridad.cookie-segura:false}") boolean cookieSegura) {
        this.gestionCajas = gestionCajas;
        this.cookieSegura = cookieSegura;
    }

    /** Las reglas de formato se validan en {@link GestionCajas} para que los rechazos queden en la bitácora. */
    public record SolicitudCaja(@NotNull @Size(max = 100) String nombre, @Size(max = 40) String serieFel) {}

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public List<Caja> listar() {
        return gestionCajas.listar();
    }

    /** A qué caja está vinculado este equipo (por su cookie); 204 si a ninguna. */
    @GetMapping("/equipo")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public ResponseEntity<Caja> equipo(@CookieValue(name = IdentificadorEquipo.COOKIE, required = false) String token) {
        return gestionCajas.delEquipo(token).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Caja obtener(@PathVariable long id) {
        return gestionCajas.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Caja crear(@Valid @RequestBody SolicitudCaja solicitud, @AuthenticationPrincipal SesionActiva actor) {
        return gestionCajas.crear(solicitud.nombre(), solicitud.serieFel(), actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Caja editar(@PathVariable long id, @Valid @RequestBody SolicitudCaja solicitud,
                       @AuthenticationPrincipal SesionActiva actor) {
        return gestionCajas.editar(id, solicitud.nombre(), solicitud.serieFel(), actor);
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Caja desactivar(@PathVariable long id, @AuthenticationPrincipal SesionActiva actor) {
        return gestionCajas.desactivar(id, actor);
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public Caja activar(@PathVariable long id, @AuthenticationPrincipal SesionActiva actor) {
        return gestionCajas.activar(id, actor);
    }

    /**
     * Vincula el equipo desde el que se hace la petición a la caja (crit. 4). El token viaja solo en una cookie
     * HttpOnly: JavaScript no la puede leer, y la base de datos solo guarda su hash.
     */
    @PostMapping("/{id}/equipo")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public ResponseEntity<Caja> vincularEquipo(@PathVariable long id, @AuthenticationPrincipal SesionActiva actor) {
        EquipoVinculado vinculo = gestionCajas.vincularEquipo(id, actor);
        ResponseCookie cookie = ResponseCookie.from(IdentificadorEquipo.COOKIE, vinculo.token())
                .httpOnly(true)
                .secure(cookieSegura)
                .sameSite("Strict")
                .path("/")
                .maxAge(DURACION_COOKIE_EQUIPO)
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(vinculo.caja());
    }
}
