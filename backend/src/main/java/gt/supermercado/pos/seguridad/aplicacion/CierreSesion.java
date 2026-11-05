package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.ConflictoException;
import gt.supermercado.pos.seguridad.dominio.RepositorioSesiones;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.VerificadorVentaEnCurso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierre de la sesión actual (HU-01.16). Solo cierra esta sesión: las del mismo usuario en otros equipos siguen
 * abiertas, y el turno de caja no se toca (crit. 3, el cierre de caja es EP-09).
 */
@Service
public class CierreSesion {

    static final String MODULO = "seguridad";
    static final String ACCION = "CIERRE_SESION";

    private final RepositorioSesiones sesiones;
    private final VerificadorVentaEnCurso ventas;
    private final Bitacora bitacora;

    public CierreSesion(RepositorioSesiones sesiones, VerificadorVentaEnCurso ventas, Bitacora bitacora) {
        this.sesiones = sesiones;
        this.ventas = ventas;
        this.bitacora = bitacora;
    }

    @Transactional
    public void cerrar(SesionActiva sesion) {
        if (ventas.tieneVentaEnCurso(sesion.usuarioId())) {
            bitacora.registrarIntentoFallido(evento(sesion, false, "Cierre de sesión rechazado: tiene una venta en curso"));
            throw new ConflictoException("VENTA_EN_CURSO",
                    "Tiene una venta en curso: finalícela, suspéndala o cancélela antes de cerrar sesión.");
        }
        sesiones.cerrar(sesion.sesionId());
        bitacora.registrar(evento(sesion, true, "Cierre de sesión"));
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), MODULO, ACCION, exitoso, descripcion,
                "usuario", sesion.usuarioId());
    }
}
