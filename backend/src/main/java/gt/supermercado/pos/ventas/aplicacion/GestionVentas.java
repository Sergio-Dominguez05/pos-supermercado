package gt.supermercado.pos.ventas.aplicacion;

import gt.supermercado.pos.caja.aplicacion.GestionTurnos;
import gt.supermercado.pos.caja.dominio.Turno;
import gt.supermercado.pos.impuestos.dominio.TasaIva;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.ventas.dominio.RepositorioVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Venta en curso del cajero (EP-06). Toda venta pertenece al turno propio abierto en la caja de la sesión
 * (HU-09.02 crit. 2): sin turno, se rechaza con {@code CAJA_CERRADA} (HU-06.01 crit. 2).
 */
@Service
public class GestionVentas {

    private final RepositorioVentas ventas;
    private final GestionTurnos turnos;

    public GestionVentas(RepositorioVentas ventas, GestionTurnos turnos) {
        this.ventas = ventas;
        this.turnos = turnos;
    }

    /** La venta en curso del turno del cajero, si tiene una. */
    @Transactional(readOnly = true)
    public Optional<Venta> actual(SesionActiva sesion) {
        return ventas.enCursoDelTurno(turnos.exigirTurnoAbierto(sesion).id());
    }

    /**
     * HU-06.01: deja la caja lista para recibir productos (crit. 1). Si ya hay una venta en curso en el turno, la
     * devuelve; si no, crea una con un número interno único (crit. 4) y la tasa de IVA vigente (HU-04.01 crit. 3).
     */
    @Transactional
    public Venta iniciar(SesionActiva sesion) {
        Turno turno = turnos.exigirTurnoAbierto(sesion);
        long id = ventas.iniciar(turno.id(), turno.cajaId(), sesion.usuarioId(), TasaIva.VIGENTE.valor());
        return ventas.buscarPorId(id).orElseThrow();
    }
}
