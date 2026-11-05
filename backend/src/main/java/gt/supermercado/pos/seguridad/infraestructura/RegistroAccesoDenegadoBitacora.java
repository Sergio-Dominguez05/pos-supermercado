package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.RegistroAccesoDenegado;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Registra en la bitácora, con el usuario y el rol de la sesión, cada acción rechazada por falta de permiso.
 * Va en una transacción aparte para que quede aunque la operación se revierta (HU-11.01 crit. 3).
 */
@Component
class RegistroAccesoDenegadoBitacora implements RegistroAccesoDenegado {

    private final Bitacora bitacora;

    RegistroAccesoDenegadoBitacora(Bitacora bitacora) {
        this.bitacora = bitacora;
    }

    @Override
    public void registrar(String metodo, String ruta) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof SesionActiva sesion)) {
            return;   // sin sesión la API responde 401 antes de llegar aquí; no hay actor que registrar
        }
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), "seguridad",
                "ACCESO_DENEGADO", false, "Acción sin permiso rechazada: %s %s".formatted(metodo, ruta), null, null));
    }
}
