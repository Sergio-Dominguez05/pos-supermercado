package gt.supermercado.pos.catalogo.infraestructura;

import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Fila;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HU-03.05: lee el archivo de carga. Reconoce el Excel (.xlsx) por su firma de ZIP; lo demás se lee como CSV en UTF-8,
 * separado por comas o, si los títulos vienen con punto y coma (como lo guarda Excel en español), por punto y coma.
 * Las columnas se buscan por su título, sin importar mayúsculas ni tildes, así que su orden no importa y las extra se
 * ignoran. Un código escrito como número en Excel se lee sin decimales ni notación científica.
 */
@Component
class LectorArchivoCatalogo implements ArchivoCatalogo.Lector {

    /** Un tope para que un archivo equivocado no recorra un millón de renglones. */
    static final int FILAS_MAXIMAS = 20_000;

    private final DataFormatter formato = new DataFormatter(Locale.ROOT);

    @Override
    public List<Fila> leer(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new NegocioException("ARCHIVO_VACIO", "El archivo está vacío.");
        }
        List<List<String>> renglones = esXlsx(contenido) ? excel(contenido) : csv(contenido);
        if (renglones.isEmpty()) {
            throw new NegocioException("ARCHIVO_VACIO", "El archivo está vacío.");
        }
        List<String> titulos = renglones.getFirst().stream().map(LectorArchivoCatalogo::clave).toList();
        int codigo = titulos.indexOf(clave(ArchivoCatalogo.CODIGO));
        int descripcion = titulos.indexOf(clave(ArchivoCatalogo.DESCRIPCION));
        int precio = titulos.indexOf(clave(ArchivoCatalogo.PRECIO));
        if (codigo < 0 || descripcion < 0 || precio < 0) {
            throw new NegocioException("PLANTILLA_INVALIDA", "El archivo debe tener las columnas Código, Descripción y "
                    + "Precio en el primer renglón. Descargue la plantilla para ver el formato.");
        }
        if (renglones.size() - 1 > FILAS_MAXIMAS) {
            throw new NegocioException("ARCHIVO_DEMASIADO_GRANDE",
                    "El archivo tiene más de %,d productos; divídalo en varios archivos.".formatted(FILAS_MAXIMAS));
        }
        List<Fila> filas = new ArrayList<>();
        for (int i = 1; i < renglones.size(); i++) {
            List<String> renglon = renglones.get(i);
            Fila fila = new Fila(i + 1, celda(renglon, codigo), celda(renglon, descripcion), celda(renglon, precio));
            if (!fila.codigo().isBlank() || !fila.descripcion().isBlank() || !fila.precio().isBlank()) {
                filas.add(fila);
            }
        }
        return filas;
    }

    /** El título sin tildes, sin espacios ni mayúsculas: "Código" y "CODIGO" son la misma columna. */
    static String clave(String titulo) {
        String sinTildes = Normalizer.normalize(titulo == null ? "" : titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.replace("﻿", "").strip().toLowerCase(Locale.ROOT);
    }

    private static String celda(List<String> renglon, int columna) {
        return columna < renglon.size() && renglon.get(columna) != null ? renglon.get(columna).strip() : "";
    }

    private static boolean esXlsx(byte[] contenido) {
        return contenido.length > 3 && contenido[0] == 'P' && contenido[1] == 'K' && contenido[2] == 3
                && contenido[3] == 4;
    }

    private List<List<String>> excel(byte[] contenido) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet hoja = libro.getSheetAt(0);
            List<List<String>> renglones = new ArrayList<>();
            int ultimo = Math.min(hoja.getLastRowNum(), FILAS_MAXIMAS + 1);
            for (int r = 0; r <= ultimo; r++) {
                Row fila = hoja.getRow(r);
                List<String> celdas = new ArrayList<>();
                if (fila != null) {
                    for (int c = 0; c < Math.max(0, fila.getLastCellNum()); c++) {
                        celdas.add(texto(fila.getCell(c)));
                    }
                }
                renglones.add(celdas);
            }
            if (hoja.getLastRowNum() > FILAS_MAXIMAS + 1) {
                renglones.add(List.of("x"));   // para que se detecte que pasa del tope
            }
            return renglones;
        } catch (Exception error) {
            throw new NegocioException("ARCHIVO_ILEGIBLE", "No se pudo leer el archivo de Excel (.xlsx).");
        }
    }

    /** Un número entero (por ejemplo, un código EAN) se lee sin ".0" ni notación científica. */
    private String texto(Cell celda) {
        if (celda == null) {
            return "";
        }
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        if (tipo == CellType.NUMERIC) {
            BigDecimal numero = BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros();
            return numero.scale() <= 0 ? numero.toBigInteger().toString() : numero.toPlainString();
        }
        return formato.formatCellValue(celda);
    }

    private static List<List<String>> csv(byte[] contenido) {
        String texto = new String(contenido, StandardCharsets.UTF_8);
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        int finTitulos = texto.indexOf('\n');
        String titulos = finTitulos < 0 ? texto : texto.substring(0, finTitulos);
        char separador = titulos.contains(";") && !titulos.contains(",") ? ';' : ',';
        List<List<String>> renglones = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    celda.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    celda.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == separador) {
                actual.add(celda.toString());
                celda.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                actual.add(celda.toString());
                celda.setLength(0);
                renglones.add(actual);
                actual = new ArrayList<>();
                if (renglones.size() > FILAS_MAXIMAS + 1) {
                    renglones.add(List.of("x"));
                    return renglones;
                }
            } else {
                celda.append(c);
            }
        }
        if (!celda.isEmpty() || !actual.isEmpty()) {
            actual.add(celda.toString());
            renglones.add(actual);
        }
        return renglones;
    }
}
