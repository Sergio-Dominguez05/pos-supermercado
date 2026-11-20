package gt.supermercado.pos.reportes.dominio;

/** Los formatos a los que se exportan los reportes: PDF (HU-10.13) y Excel .xlsx (HU-10.14). */
public enum FormatoExportacion {

    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String tipoContenido;

    FormatoExportacion(String extension, String tipoContenido) {
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
