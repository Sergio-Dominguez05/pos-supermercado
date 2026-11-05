package gt.supermercado.pos.seguridad.infraestructura;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Cookie que lleva el token de sesión (Architecture.md §6.5). {@code HttpOnly} para que JavaScript no la lea
 * y {@code SameSite=Strict} para que otros sitios no puedan usarla (protección CSRF).
 * {@code Secure} se activa en producción, donde la LAN usa HTTPS.
 */
@Component
public class CookieSesion {

    public static final String NOMBRE = "POS_SESION";

    private final boolean segura;

    public CookieSesion(@Value("${pos.seguridad.cookie-segura:false}") boolean segura) {
        this.segura = segura;
    }

    public ResponseCookie crear(String token) {
        return ResponseCookie.from(NOMBRE, token)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path("/")
                .build();
    }

    /** Misma cookie vacía y vencida: el navegador la borra al cerrar sesión. */
    public ResponseCookie borrar() {
        return ResponseCookie.from(NOMBRE, "")
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    public Optional<String> leer(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NOMBRE.equals(cookie.getName()) && !cookie.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }
}
