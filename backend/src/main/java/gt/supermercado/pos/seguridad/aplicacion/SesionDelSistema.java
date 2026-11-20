package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * HU-08.08: la "sesión" de los procesos automáticos (decisión de Sergio). Sus acciones quedan en la bitácora a nombre
 * del usuario técnico "sistema" (V31), que no tiene permisos ni puede iniciar sesión.
 */
@Service
public class SesionDelSistema {

    private final RepositorioUsuarios usuarios;
    private volatile SesionActiva sesion;

    public SesionDelSistema(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public SesionActiva sesion() {
        SesionActiva actual = sesion;
        if (actual == null) {
            actual = new SesionActiva(0, usuarios.idDelSistema(), "sistema", "Proceso automático del sistema",
                    Rol.SISTEMA, "Sistema", false, Set.of());
            sesion = actual;
        }
        return actual;
    }
}
