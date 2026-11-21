package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.FormatoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroConsultado;
import gt.supermercado.pos.bitacora.dominio.RegistroExportado;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11.09 crit. 1: el CSV y el Excel de la bitácora, con las mismas columnas y sin fórmulas inyectadas. */
class ExportadorBitacoraArchivoTest {

    private static final RegistroExportado REGISTRO = new RegistroExportado(new RegistroConsultado(1, 7,
            OffsetDateTime.parse("2026-11-20T16:30:05Z"), "Ana López (ana)", "ADMIN", "Caja 1", "catalogo",
            "PRODUCTO_EDITADO", true, "=HYPERLINK(\"x\") editado, con coma"), "producto", 9L,
            "{\"descripcion\": {\"antes\": \"A\", \"despues\": \"B\"}}");

    @Test
    void elCsvEscapaComasYComillasYEvitaFormulas() {
        String csv = new String(new ExportadorBitacoraArchivo().exportar(List.of(REGISTRO), FormatoBitacora.CSV),
                StandardCharsets.UTF_8);

        String[] lineas = csv.split("\r\n");
        assertThat(lineas[0]).startsWith("﻿Secuencia,Fecha y hora,Usuario");
        assertThat(lineas[1]).startsWith("7,2026-11-20 10:30:05,Ana López (ana),ADMIN,Caja 1,catalogo,PRODUCTO_EDITADO,"
                + "Exitoso,\"'=HYPERLINK(\"\"x\"\") editado, con coma\",producto,9,");
        assertThat(lineas[1]).endsWith("\"{\"\"descripcion\"\": {\"\"antes\"\": \"\"A\"\", \"\"despues\"\": \"\"B\"\"}}\"");
    }

    @Test
    void lasCeldasNulasQuedanVaciasYSoloSeProtegenLosTextosQueParecenFormulas() {
        assertThat(ExportadorBitacoraArchivo.seguro(null)).isEmpty();
        assertThat(ExportadorBitacoraArchivo.seguro("-5")).isEqualTo("'-5");
        assertThat(ExportadorBitacoraArchivo.seguro("@SUM")).isEqualTo("'@SUM");
        assertThat(ExportadorBitacoraArchivo.seguro("Precio a Q9.25")).isEqualTo("Precio a Q9.25");
    }

    @Test
    void elExcelEsUnLibroValido() throws Exception {
        byte[] excel = new ExportadorBitacoraArchivo().exportar(List.of(REGISTRO), FormatoBitacora.XLSX);

        try (var libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
            var hoja = libro.getSheet("Bitácora");
            assertThat(hoja.getRow(0).getCell(11).getStringCellValue()).isEqualTo("Cambios");
            assertThat(hoja.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(7);
            assertThat(hoja.getRow(1).getCell(8).getStringCellValue()).startsWith("'=HYPERLINK");
        }
    }
}
