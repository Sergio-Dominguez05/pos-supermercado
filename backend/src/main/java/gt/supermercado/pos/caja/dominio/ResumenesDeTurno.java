package gt.supermercado.pos.caja.dominio;

/**
 * Lo cobrado en un turno, a partir de las ventas, los pagos, las facturas y las anulaciones del turno. Se calcula
 * siempre de nuevo: el turno cerrado ya no cambia, así que la reimpresión (HU-09.10 crit. 3) da lo mismo.
 */
public interface ResumenesDeTurno {

    ResumenDeTurno de(long turnoId);
}
