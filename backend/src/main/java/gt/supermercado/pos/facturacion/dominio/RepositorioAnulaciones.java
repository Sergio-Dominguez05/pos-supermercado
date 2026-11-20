package gt.supermercado.pos.facturacion.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** HU-08.10: la búsqueda de facturas emitidas y su anulación. */
public interface RepositorioAnulaciones {

    /**
     * Crit. 1: las facturas certificadas o anuladas cuyo número, serie o autorización contienen {@code texto}, y que se
     * emitieron en {@code fecha} (hora de Guatemala). Cualquiera de los dos puede faltar. Las más recientes primero.
     */
    List<FacturaEmitida> buscar(String texto, LocalDate fecha, int limite);

    Optional<FacturaEmitida> porId(long facturaId);

    /**
     * Crit. 3 y 4: la factura queda {@code ANULADA} con su anulación (motivo, usuario, turno de la venta y respuesta del
     * certificador). Falso si ya no estaba certificada.
     */
    boolean anular(long facturaId, String motivo, long usuarioId, long turnoId, String respuestaJson);

    /** Crit. 3: un intento de anular que el certificador rechazó o no respondió. */
    void intentoFallido(long facturaId, String codigo, String respuestaJson);
}
