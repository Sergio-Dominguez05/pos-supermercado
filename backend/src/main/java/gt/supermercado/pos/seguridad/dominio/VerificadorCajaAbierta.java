package gt.supermercado.pos.seguridad.dominio;

/**
 * Indica si un usuario tiene un turno de caja abierto, para no desactivarlo sin cerrar la caja (HU-01.08 crit. 3).
 * Lo implementará el módulo {@code caja} cuando existan los turnos (HU-09.01), que depende de {@code seguridad}
 * y no al revés (Architecture.md §4).
 */
public interface VerificadorCajaAbierta {

    boolean tieneCajaAbierta(long usuarioId);
}
