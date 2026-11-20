package gt.supermercado.pos.reportes.infraestructura;

import gt.supermercado.pos.reportes.dominio.ExportadorReportes;
import gt.supermercado.pos.reportes.dominio.FormatoExportacion;
import gt.supermercado.pos.reportes.dominio.TablaReporte;
import org.springframework.stereotype.Component;

/** Genera el archivo con OpenPDF (HU-10.13) o Apache POI (HU-10.14), como propone Architecture.md §3. */
@Component
class ExportadorReportesArchivo implements ExportadorReportes {

    @Override
    public byte[] exportar(TablaReporte tabla, FormatoExportacion formato) {
        return switch (formato) {
            case PDF -> PdfDeReporte.generar(tabla);
            case XLSX -> ExcelDeReporte.generar(tabla);
        };
    }
}
