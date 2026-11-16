package gt.supermercado.pos.ventas.dominio;

/** Ciclo de vida de una venta (Architecture.md §6.1). */
public enum EstadoVenta {
    EN_CURSO, SUSPENDIDA, CANCELADA, FINALIZADA
}
