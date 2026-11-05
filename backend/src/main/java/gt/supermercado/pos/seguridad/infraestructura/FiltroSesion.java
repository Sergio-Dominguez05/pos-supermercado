package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.aplicacion.ConsultaSesion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Autentica cada petición con la cookie de sesión. Las autoridades son los permisos copiados al iniciar
 * sesión más {@code ROL_<codigo>}; con ellas cada endpoint podrá exigir su permiso (HU-01.11).
 * Sin cookie, o con una sesión cerrada o de un usuario desactivado, la petición sigue como anónima.
 */
class FiltroSesion extends OncePerRequestFilter {

    private final CookieSesion cookie;
    private final ConsultaSesion consultaSesion;

    FiltroSesion(CookieSesion cookie, ConsultaSesion consultaSesion) {
        this.cookie = cookie;
        this.consultaSesion = consultaSesion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        cookie.leer(peticion)
                .flatMap(consultaSesion::porToken)
                .ifPresent(FiltroSesion::autenticar);
        cadena.doFilter(peticion, respuesta);
    }

    private static void autenticar(SesionActiva sesion) {
        List<SimpleGrantedAuthority> autoridades = Stream.concat(
                        sesion.permisos().stream(), Stream.of("ROL_" + sesion.rol().name()))
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sesion, null, autoridades));
    }
}
