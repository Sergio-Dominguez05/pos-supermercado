package gt.supermercado.pos.reportes.dominio;

/** Convierte un reporte en un archivo PDF (HU-10.13) o Excel (HU-10.14). Lo implementa la infraestructura. */
public interface ExportadorReportes {

    byte[] exportar(TablaReporte tabla, FormatoExportacion formato);
}
