package gt.supermercado.pos.facturacion.dominio;

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

    /** El certificador la rechazó: queda el intento con el error (HU-08.06 lo corrige y la reenvía). */
    boolean rechazada(long facturaId, ResultadoCertificacion resultado);

    /** HU-08.03 crit. 3: sin respuesta a tiempo. La tarea queda pendiente para reintentarla (HU-08.07 y HU-08.08). */
    boolean enContingencia(long facturaId, String motivo);
}
