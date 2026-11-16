package gt.supermercado.pos.caja.dominio;

/**
 * HU-06.12 crit. 4: antes de cerrar la caja hay que retomar o cancelar las ventas suspendidas del turno. El módulo
 * {@code ventas} lo implementa; {@code caja} no depende de él.
 */
public interface VerificadorVentasSuspendidas {

    /** Cuántas ventas suspendidas tiene el turno. */
    int suspendidasDelTurno(long turnoId);
}
