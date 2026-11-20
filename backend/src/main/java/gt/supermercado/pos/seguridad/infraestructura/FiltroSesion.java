package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.aplicacion.ConsultaSesion;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TipoCredencial;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Autentica cada petición con la cookie de sesión. Las autoridades son los permisos copiados al iniciar
 * sesión más {@code ROL_<codigo>}; con ellas cada endpoint podrá exigir su permiso (HU-01.11).
 * Sin cookie, o con una sesión cerrada o de un usuario desactivado, la petición sigue como anónima.
 *
 * <p>HU-01.15: mientras la contraseña o el PIN sea temporal (usuario nuevo o credencial restablecida), solo se
 * permite consultar la sesión, cambiar la credencial y cerrar la sesión (crit. 1 y 2); todo lo demás responde 403
 * con el código {@code CREDENCIAL_TEMPORAL}. La marca se lee de la base en cada petición, así que deja de aplicar en
 * cuanto el usuario cambia su credencial.
 */
class FiltroSesion extends OncePerRequestFilter {

    /** Lo único que puede hacer quien tiene una credencial temporal: método y ruta. */
    static final Set<String> PERMITIDO_CON_CREDENCIAL_TEMPORAL = Set.of(
            "GET /api/auth/sesion", "PUT /api/auth/credencial", "POST /api/auth/logout");

    private final CookieSesion cookie;
    private final ConsultaSesion consultaSesion;

    FiltroSesion(CookieSesion cookie, ConsultaSesion consultaSesion) {
        this.cookie = cookie;
        this.consultaSesion = consultaSesion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Optional<SesionActiva> sesion = cookie.leer(peticion).flatMap(consultaSesion::porToken);
        if (sesion.isPresent() && sesion.get().credencialTemporal() && !permitidoConCredencialTemporal(peticion)) {
            rechazarPorCredencialTemporal(sesion.get(), respuesta);
            return;
        }
        sesion.ifPresent(FiltroSesion::autenticar);
        cadena.doFilter(peticion, respuesta);
    }

    static boolean permitidoConCredencialTemporal(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
        // Solo la API está protegida; la aplicación web y los recursos estáticos no pasan por aquí en producción.
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/sistema/")
                || PERMITIDO_CON_CREDENCIAL_TEMPORAL.contains(peticion.getMethod() + " " + ruta);
    }

    private static void rechazarPorCredencialTemporal(SesionActiva sesion, HttpServletResponse respuesta)
            throws IOException {
        boolean esPin = TipoCredencial.de(sesion.rol()) == TipoCredencial.PIN;
        respuesta.setStatus(HttpServletResponse.SC_FORBIDDEN);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("""
                {"codigo":"CREDENCIAL_TEMPORAL","mensaje":"%s","detalles":[]}""".formatted(esPin
                ? "Su PIN es temporal: debe cambiarlo antes de continuar."
                : "Su contraseña es temporal: debe cambiarla antes de continuar."));
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
