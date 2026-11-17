package gt.supermercado.pos.pagos.dominio;

import java.math.BigDecimal;
import java.util.List;

public interface RepositorioPagos {

    /** Registra el pago con el turno y el usuario que lo cobró (HU-09.02 crit. 2) y devuelve su id. */
    long registrar(long ventaId, long turnoId, long usuarioId, TipoTarjeta tipo, String autorizacion, String ultimos4,
                   BigDecimal monto);

    /** Los pagos de la venta, en el orden en que se registraron. */
    List<Pago> deLaVenta(long ventaId);

    /** Si la venta tiene al menos un pago registrado. */
    boolean tienePagos(long ventaId);
}
