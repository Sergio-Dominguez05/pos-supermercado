package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.bitacora.dominio.ContextoEvento;
import gt.supermercado.pos.seguridad.dominio.IdentificadorEquipo;
import gt.supermercado.pos.seguridad.dominio.IdentificadorEquipo.CajaDelEquipo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TokenSesion;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * Caja donde ocurre cada evento de la bitácora (HU-11.02 crit. 1):
 * con sesión, la caja de la sesión; sin sesión (al iniciarla, aunque falle), la caja del equipo vinculado;
 * fuera de una petición web (al arrancar el sistema), ninguna.
 */
@Component
class ContextoEventoSesion implements ContextoEvento {

    private final IdentificadorEquipo equipos;

    ContextoEventoSesion(IdentificadorEquipo equipos) {
        this.equipos = equipos;
    }

    @Override
    public Long cajaActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof SesionActiva sesion) {
            return sesion.cajaId();
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        Cookie[] cookies = atributos.getRequest().getCookies();
        return cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> IdentificadorEquipo.COOKIE.equals(c.getName()) && !c.getValue().isBlank())
                .findFirst()
                .flatMap(c -> equipos.cajaActiva(TokenSesion.hash(c.getValue())))
                .map(CajaDelEquipo::id)
                .orElse(null);
    }
}
