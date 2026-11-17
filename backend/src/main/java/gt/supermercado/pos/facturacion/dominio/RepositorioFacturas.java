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

    /** Crit. 2: guarda la autorización, la serie, el número y la fecha, y completa la tarea. */
    void certificada(long facturaId, ResultadoCertificacion resultado);

    /** El certificador la rechazó: queda el intento con el error (HU-08.06 lo corrige y la reenvía). */
    void rechazada(long facturaId, ResultadoCertificacion resultado);

    /** Crit. 3: sin respuesta a tiempo. La tarea queda pendiente para reintentarla (HU-08.07 y HU-08.08). */
    void enContingencia(long facturaId, String motivo);
}
