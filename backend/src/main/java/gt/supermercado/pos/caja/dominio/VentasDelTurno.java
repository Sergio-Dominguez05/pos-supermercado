package gt.supermercado.pos.caja.dominio;

/**
 * HU-09.08 crit. 1: la venta en curso del turno, que se debe resolver antes de cerrar la caja. Lo implementa el módulo
 * {@code ventas}.
 */
public interface VentasDelTurno {

    /** Una venta en curso con productos: el cajero debe cobrarla o cancelarla antes de cerrar. */
    boolean tieneVentaEnCursoConProductos(long turnoId);

    /** La venta en curso vacía (la pantalla siempre deja una lista) se cancela al cerrar: no tiene nada que resolver. */
    void cancelarVentaVaciaEnCurso(long turnoId);
}
