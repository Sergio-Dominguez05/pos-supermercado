package gt.supermercado.pos.reportes.dominio;

/** HU-10.01 crit. 2: el filtro por tipo de tarjeta, en los reportes donde aplica (por ejemplo, HU-10.06). */
public enum TipoTarjetaReporte {

    DEBITO("Débito"),
    CREDITO("Crédito");

    private final String etiqueta;

    TipoTarjetaReporte(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
