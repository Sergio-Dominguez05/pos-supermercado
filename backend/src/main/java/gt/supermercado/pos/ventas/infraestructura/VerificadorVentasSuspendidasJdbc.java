package gt.supermercado.pos.ventas.infraestructura;

import gt.supermercado.pos.caja.dominio.VerificadorVentasSuspendidas;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import org.springframework.stereotype.Component;

/** HU-06.12 crit. 4: las ventas suspendidas del turno, para que la caja no se cierre con ventas en espera. */
@Component
class VerificadorVentasSuspendidasJdbc implements VerificadorVentasSuspendidas {

    private final RepositorioVentas ventas;

    VerificadorVentasSuspendidasJdbc(RepositorioVentas ventas) {
        this.ventas = ventas;
    }

    @Override
    public int suspendidasDelTurno(long turnoId) {
        return ventas.suspendidasDelTurno(turnoId);
    }
}
