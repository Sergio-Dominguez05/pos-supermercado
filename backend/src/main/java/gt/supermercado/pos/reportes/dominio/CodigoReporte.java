package gt.supermercado.pos.reportes.dominio;

/**
 * Los reportes de EP-10 y sus detalles: cada uno se ve en pantalla y se exporta a PDF o Excel (HU-10.13 y HU-10.14,
 * decisión de Sergio: también los detalles). El código es el que usa la ruta de exportación.
 */
public enum CodigoReporte {

    VENTAS_PERIODO(ReporteVentasPeriodo.TITULO, "ventas-por-periodo"),
    VENTAS_CAJERO(ReporteVentasPorCajero.TITULO, "ventas-por-cajero"),
    DETALLE_CAJERO(DetalleVentasCajero.TITULO, "ventas-del-cajero"),
    VENTAS_CAJA(ReporteVentasPorCaja.TITULO, "ventas-por-caja"),
    PRODUCTOS(ReporteProductosMasVendidos.TITULO, "productos-mas-vendidos"),
    TARJETAS(ReporteTarjetas.TITULO, "ingresos-por-tarjeta"),
    DETALLE_TARJETA(DetallePagosTarjeta.TITULO, "transacciones-con-tarjeta"),
    IVA(ReporteIva.TITULO, "iva-cobrado"),
    DETALLE_IVA(DetalleFacturasIva.TITULO, "iva-por-factura"),
    CIERRES(ReporteCierres.TITULO, "cierres-de-caja"),
    ANULADAS(ReporteAnuladas.TITULO, "facturas-anuladas");

    private final String titulo;
    private final String archivo;

    CodigoReporte(String titulo, String archivo) {
        this.titulo = titulo;
        this.archivo = archivo;
    }

    public String titulo() {
        return titulo;
    }

    /** El inicio del nombre del archivo exportado, sin tildes ni espacios. */
    public String archivo() {
        return archivo;
    }
}
