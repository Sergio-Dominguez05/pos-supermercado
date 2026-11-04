package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.seguridad.dominio.RepositorioSesiones;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TokenSesion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Resuelve el token de la cookie en la sesión del usuario, en cada petición. */
@Service
public class ConsultaSesion {

    private final RepositorioSesiones sesiones;

    public ConsultaSesion(RepositorioSesiones sesiones) {
        this.sesiones = sesiones;
    }

    @Transactional(readOnly = true)
    public Optional<SesionActiva> porToken(String token) {
        return sesiones.buscarActiva(TokenSesion.hash(token));
    }
}
