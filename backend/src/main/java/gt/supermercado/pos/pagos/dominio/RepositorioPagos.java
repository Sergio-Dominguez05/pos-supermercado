package gt.supermercado.pos.pagos.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepositorioPagos {

    /** Registra el pago con el turno y el usuario que lo cobró (HU-09.02 crit. 2) y devuelve su id. */
    long registrar(long ventaId, long turnoId, long usuarioId, TipoTarjeta tipo, String autorizacion, String ultimos4,
                   BigDecimal monto);

    /** Los pagos vigentes de la venta (sin los eliminados, HU-07.06), en el orden en que se registraron. */
    List<Pago> deLaVenta(long ventaId);

    /** Si la venta tiene al menos un pago vigente. */
    boolean tienePagos(long ventaId);

    /** El pago vigente, solo si es de esa venta: nadie elimina pagos de otra venta con su id. */
    Optional<Pago> deLaVenta(long ventaId, long pagoId);

    /**
     * HU-07.06: marca el pago como eliminado por el usuario; deja de contar para la venta, pero no se borra. Devuelve
     * {@code false} si ya estaba eliminado.
     */
    boolean eliminar(long pagoId, long usuarioId);
}
