package gt.supermercado.pos.facturacion.dominio;

/** Estados de la factura (Architecture.md §6.3). */
public enum EstadoFactura {
    /** Guardada con la venta; todavía no se envió al certificador o no se sabe la respuesta. */
    PENDIENTE,
    /** El certificador la aceptó: tiene autorización, serie, número y fecha (HU-08.03 crit. 2). */
    CERTIFICADA,
    /** El certificador no respondió a tiempo (HU-08.03 crit. 3, HU-08.07). */
    CONTINGENCIA,
    /** El certificador la rechazó por un error en los datos (HU-08.06). */
    RECHAZADA,
    /** Anulada después de certificada (HU-08.10). */
    ANULADA
}
