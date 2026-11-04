package gt.supermercado.pos.seguridad.infraestructura;

import gt.supermercado.pos.seguridad.dominio.VerificadorCajaAbierta;
import org.springframework.stereotype.Component;

/**
 * Mientras no existan los turnos de caja nadie puede tener una caja abierta.
 * HU-09.01 debe reemplazar esta clase por una implementación del módulo {@code caja} que consulte
 * {@code turno WHERE usuario_id = ? AND estado = 'ABIERTO'}, y borrar esta.
 */
@Component
class SinTurnosDeCaja implements VerificadorCajaAbierta {

    @Override
    public boolean tieneCajaAbierta(long usuarioId) {
        return false;
    }
}
