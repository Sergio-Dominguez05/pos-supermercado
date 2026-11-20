package gt.supermercado.pos.reportes.dominio;

/** HU-10.05 crit. 2: el criterio con que se ordena el reporte de productos más vendidos. */
public enum OrdenProductos {

    CANTIDAD("Cantidad vendida"),
    MONTO("Monto vendido");

    private final String etiqueta;

    OrdenProductos(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
