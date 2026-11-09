package gt.supermercado.pos.caja.infraestructura;

import gt.supermercado.pos.caja.dominio.RepositorioTurnos;
import gt.supermercado.pos.configuracion.dominio.VerificadorTurnoAbiertoCaja;
import gt.supermercado.pos.seguridad.dominio.VerificadorCajaAbierta;
import org.springframework.stereotype.Component;

/**
 * Responde a {@code seguridad} y {@code configuracion} si hay turnos abiertos, sin que esos módulos dependan de
 * {@code caja} (Architecture.md §4). Reemplaza a las implementaciones provisionales que respondían siempre "no":
 *
 * <ul>
 *   <li>HU-01.08 crit. 3: no se desactiva a un usuario con su caja abierta.</li>
 *   <li>HU-12.05 crit. 3: no se desactiva una caja con un turno abierto.</li>
 * </ul>
 */
@Component
class VerificadorTurnosJdbc implements VerificadorCajaAbierta, VerificadorTurnoAbiertoCaja {

    private final RepositorioTurnos turnos;

    VerificadorTurnosJdbc(RepositorioTurnos turnos) {
        this.turnos = turnos;
    }

    @Override
    public boolean tieneCajaAbierta(long usuarioId) {
        return turnos.abiertoDeUsuario(usuarioId).isPresent();
    }

    @Override
    public boolean tieneTurnoAbierto(long cajaId) {
        return turnos.abiertoEnCaja(cajaId).isPresent();
    }
}
