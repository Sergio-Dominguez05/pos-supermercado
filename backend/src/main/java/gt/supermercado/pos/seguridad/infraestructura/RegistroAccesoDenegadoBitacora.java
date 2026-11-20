package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.RegistroAccesoDenegado;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra en la bitácora, con el usuario, el rol y la caja de la sesión, cada acción rechazada por falta de permiso,
 * con el permiso que le faltaba (HU-11.04).
 * Va en una transacción aparte para que quede aunque la operación se revierta (HU-11.01 crit. 3).
 */
@Component
class RegistroAccesoDenegadoBitacora implements RegistroAccesoDenegado {

    private final Bitacora bitacora;

    RegistroAccesoDenegadoBitacora(Bitacora bitacora) {
        this.bitacora = bitacora;
    }

    @Override
    public void registrar(String metodo, String ruta, List<String> permisos) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof SesionActiva sesion)) {
            return;   // sin sesión la API responde 401 antes de llegar aquí; no hay actor que registrar
        }
        // HU-11.04: quién, desde qué caja, qué acción y qué permiso le faltaba.
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("metodo", metodo);
        detalle.put("ruta", ruta);
        if (!permisos.isEmpty()) {
            detalle.put("permisoRequerido", String.join(" o ", permisos));
        }
        String requiere = permisos.isEmpty() ? "" : " (requiere %s)".formatted(String.join(" o ", permisos));
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.ACCESO_DENEGADO, false,
                "Acción sin permiso rechazada: %s %s%s".formatted(metodo, ruta, requiere), null, null, detalle)
                .enCaja(sesion.cajaId()));
    }
}
