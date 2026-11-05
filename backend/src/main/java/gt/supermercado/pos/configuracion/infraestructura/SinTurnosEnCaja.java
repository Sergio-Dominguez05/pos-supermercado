package gt.supermercado.pos.configuracion.infraestructura;

import gt.supermercado.pos.configuracion.dominio.VerificadorTurnoAbiertoCaja;
import org.springframework.stereotype.Component;

/**
 * Mientras no existan los turnos de caja ninguna caja puede tener uno abierto.
 * HU-09.01 debe reemplazar esta clase por una implementación del módulo {@code caja} que consulte
 * {@code turno WHERE caja_id = ? AND estado = 'ABIERTO'}, y borrar esta.
 */
@Component
class SinTurnosEnCaja implements VerificadorTurnoAbiertoCaja {

    @Override
    public boolean tieneTurnoAbierto(long cajaId) {
        return false;
    }
}
