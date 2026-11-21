package gt.supermercado.pos.bitacora.dominio;

/** HU-11.09 crit. 1: los formatos a los que se exporta la bitácora. */
public enum FormatoBitacora {

    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv; charset=UTF-8");

    private final String extension;
    private final String tipoContenido;

    FormatoBitacora(String extension, String tipoContenido) {
        this.extension = extension;
        this.tipoContenido = tipoContenido;
    }

    public String extension() {
        return extension;
    }

    public String tipoContenido() {
        return tipoContenido;
    }
}
