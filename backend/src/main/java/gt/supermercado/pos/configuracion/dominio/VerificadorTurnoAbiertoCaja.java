package gt.supermercado.pos.configuracion.dominio;

/**
 * Indica si una caja tiene un turno abierto, para no desactivarla (HU-12.05 crit. 3). Lo implementará el módulo
 * {@code caja} cuando existan los turnos (HU-09.01), que depende de {@code configuracion} y no al revés.
 */
public interface VerificadorTurnoAbiertoCaja {

    boolean tieneTurnoAbierto(long cajaId);
}
