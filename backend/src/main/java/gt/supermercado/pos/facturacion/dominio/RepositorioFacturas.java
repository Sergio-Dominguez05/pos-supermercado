package gt.supermercado.pos.facturacion.dominio;

import java.util.List;
import java.util.Optional;

/** Las facturas y sus tareas de certificación (Architecture.md §5.2). */
public interface RepositorioFacturas {

    /**
     * Registra la factura {@code PENDIENTE} de la venta con la copia del receptor, del emisor y del establecimiento,
     * y la tarea de certificarla. Debe correr en la transacción que finaliza la venta.
     */
    long registrar(DatosDte datos, String xmlEnviado);

    Optional<Factura> deLaVenta(long ventaId);

    /** HU-08.05: la factura de la venta con la copia del receptor, del emisor y del establecimiento. */
    Optional<FacturaImprimible> imprimible(long ventaId);

    /*
     * Los cambios de estado solo se aplican a una factura PENDIENTE o en CONTINGENCIA y devuelven false si ya no lo
     * estaba (otro proceso la certificó antes): HU-08.04 crit. 3, una factura certificada no se vuelve a facturar.
     */

    /** HU-08.03 crit. 2: guarda la autorización, la serie, el número y la fecha, y completa la tarea. */
    boolean certificada(long facturaId, ResultadoCertificacion resultado);

    /** El certificador la rechazó: queda el intento con su respuesta completa (HU-08.06 crit. 4). */
    boolean rechazada(long facturaId, ResultadoCertificacion resultado);

    /** El último rechazo de la factura, si la rechazaron. */
    Optional<FacturaRechazada> rechazoDe(long facturaId);

    /** HU-08.06 crit. 3: las ventas cuyas facturas están rechazadas, pendientes de facturar, de la más antigua. */
    List<FacturaRechazada> rechazadas();

    /**
     * HU-08.06 crit. 2: vuelve a dejar {@code PENDIENTE} una factura rechazada, con el DTE corregido (otro receptor o la
     * configuración ya corregida) y una tarea nueva de certificarla. Falso si ya no estaba rechazada.
     */
    boolean reabrir(long facturaId, DatosDte datos, String xmlEnviado);

    /** HU-08.03 crit. 3: sin respuesta a tiempo. La tarea queda pendiente para reintentarla (HU-08.07 y HU-08.08). */
    boolean enContingencia(long facturaId, String motivo);
}
