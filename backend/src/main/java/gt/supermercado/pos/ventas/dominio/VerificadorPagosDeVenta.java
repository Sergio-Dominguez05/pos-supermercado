package gt.supermercado.pos.ventas.dominio;

/**
 * HU-07.03: con un pago registrado, la venta ya se cobró en la terminal bancaria y no se modifica (productos,
 * cantidades, suspender o cancelar) hasta que se elimine el pago (HU-07.06). El módulo {@code pagos} lo implementa;
 * {@code ventas} no depende de él.
 */
public interface VerificadorPagosDeVenta {

    boolean tienePagos(long ventaId);
}
