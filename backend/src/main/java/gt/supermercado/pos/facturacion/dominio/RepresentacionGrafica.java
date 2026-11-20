package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.15 crit. 2: la representación gráfica de la factura en PDF, con lo mismo que lleva el ticket impreso
 * (HU-08.05). La implementa el módulo {@code impresion}.
 */
public interface RepresentacionGrafica {

    byte[] pdf(long ventaId);
}
