package gt.supermercado.pos.reportes.infraestructura;

import gt.supermercado.pos.reportes.dominio.FiltroAplicado;
import gt.supermercado.pos.reportes.dominio.TablaReporte;
import gt.supermercado.pos.reportes.dominio.TablaReporte.Columna;
import gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-10.14: el reporte en Excel (.xlsx). Crit. 2: los montos y las cantidades van como números y las fechas como
 * fechas, para usarlos en fórmulas. Crit. 3: el encabezado lleva los filtros aplicados. La fila de títulos queda
 * fija al desplazarse.
 */
final class ExcelDeReporte {

    private ExcelDeReporte() {
    }

    static byte[] generar(TablaReporte tabla) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(tabla.encabezado().titulo()));
            int fila = 0;

            for (String linea : tabla.empresa().lineas()) {
                texto(hoja.createRow(fila++), 0, linea, fila == 1 ? estilos.negrita : null);
            }
            fila++;
            texto(hoja.createRow(fila++), 0, tabla.encabezado().titulo(), estilos.titulo);
            for (FiltroAplicado filtro : tabla.encabezado().filtros()) {
                Row renglon = hoja.createRow(fila++);
                texto(renglon, 0, filtro.etiqueta(), estilos.negrita);
                texto(renglon, 1, filtro.valor(), null);
            }
            Row generado = hoja.createRow(fila++);
            texto(generado, 0, "Generado", estilos.negrita);
            Cell momento = generado.createCell(1);
            momento.setCellValue(tabla.encabezado().generadoEn().atZoneSameInstant(TextosDeCelda.GUATEMALA)
                    .toLocalDateTime());
            momento.setCellStyle(estilos.estiloDe(Tipo.FECHA_HORA, false));
            texto(generado, 2, "por " + tabla.encabezado().generadoPor(), null);

            if (!tabla.resumen().isEmpty()) {
                fila++;
                for (TablaReporte.Dato dato : tabla.resumen()) {
                    Row renglon = hoja.createRow(fila++);
                    texto(renglon, 0, dato.etiqueta(), estilos.negrita);
                    valor(renglon.createCell(1), dato.valor(), dato.tipo(), estilos, false);
                }
            }

            fila++;
            List<Columna> columnas = tabla.columnas();
            int filaTitulos = fila;
            Row titulos = hoja.createRow(fila++);
            for (int i = 0; i < columnas.size(); i++) {
                texto(titulos, i, columnas.get(i).titulo(), estilos.titulosTabla);
            }
            for (List<Object> datos : tabla.filas()) {
                escribirFila(hoja.createRow(fila++), columnas, datos, estilos, false);
            }
            for (List<Object> datos : tabla.pie()) {
                escribirFila(hoja.createRow(fila++), columnas, datos, estilos, true);
            }
            if (tabla.filas().isEmpty() && tabla.pie().isEmpty()) {
                texto(hoja.createRow(fila++), 0, "No hay datos con estos filtros.", null);
            }
            for (String nota : tabla.notas()) {
                texto(hoja.createRow(++fila), 0, nota, null);
            }

            hoja.createFreezePane(0, filaTitulos + 1);
            anchos(hoja, tabla);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo generar el archivo de Excel", error);
        }
    }

    private static void escribirFila(Row renglon, List<Columna> columnas, List<Object> datos, Estilos estilos,
                                     boolean total) {
        for (int i = 0; i < columnas.size(); i++) {
            Tipo tipo = columnas.get(i).tipo();
            Object dato = datos.get(i);
            // En el pie, la primera celda es la etiqueta ("Total") aunque la columna sea de fechas o números.
            if (dato instanceof String etiqueta && tipo != Tipo.TEXTO) {
                texto(renglon, i, etiqueta, total ? estilos.negrita : null);
            } else {
                valor(renglon.createCell(i), dato, tipo, estilos, total);
            }
        }
    }

    private static void valor(Cell celda, Object dato, Tipo tipo, Estilos estilos, boolean total) {
        if (dato == null) {
            celda.setBlank();
        } else if (dato instanceof BigDecimal monto) {
            celda.setCellValue(monto.doubleValue());
        } else if (dato instanceof Long numero) {
            celda.setCellValue(numero);
        } else if (dato instanceof LocalDate fecha) {
            celda.setCellValue(fecha);
        } else if (dato instanceof OffsetDateTime momento) {
            celda.setCellValue(momento.atZoneSameInstant(TextosDeCelda.GUATEMALA).toLocalDateTime());
        } else {
            celda.setCellValue(dato.toString());
        }
        celda.setCellStyle(estilos.estiloDe(tipo, total));
    }

    private static void texto(Row renglon, int columna, String texto, CellStyle estilo) {
        Cell celda = renglon.createCell(columna);
        celda.setCellValue(texto);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    /** Anchos según el texto más largo de cada columna (sin depender de las fuentes del servidor). */
    private static void anchos(Sheet hoja, TablaReporte tabla) {
        for (int i = 0; i < tabla.columnas().size(); i++) {
            Columna columna = tabla.columnas().get(i);
            int largo = columna.titulo().length();
            for (List<Object> datos : tabla.filas()) {
                largo = Math.max(largo, TextosDeCelda.texto(datos.get(i), columna.tipo()).length());
            }
            if (i < 2) {
                largo = Math.max(largo, 18);
            }
            hoja.setColumnWidth(i, Math.min(60, largo + 3) * 256);
        }
    }

    /** Los estilos se crean una vez por libro (Excel limita su cantidad). */
    private static final class Estilos {
        final CellStyle negrita;
        final CellStyle titulo;
        final CellStyle titulosTabla;
        private final XSSFWorkbook libro;
        private final Font fuenteNegrita;
        private final java.util.Map<String, CellStyle> porTipo = new java.util.HashMap<>();

        Estilos(XSSFWorkbook libro) {
            this.libro = libro;
            fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);
            Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            negrita = libro.createCellStyle();
            negrita.setFont(fuenteNegrita);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);
            titulosTabla = libro.createCellStyle();
            titulosTabla.setFont(fuenteNegrita);
            titulosTabla.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            titulosTabla.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        CellStyle estiloDe(Tipo tipo, boolean total) {
            return porTipo.computeIfAbsent(tipo + "/" + total, clave -> {
                CellStyle estilo = libro.createCellStyle();
                String formato = switch (tipo) {
                    case DINERO -> "\"Q\"#,##0.00;-\"Q\"#,##0.00";
                    case ENTERO -> "0";
                    case PORCENTAJE -> "0.0\" %\"";
                    case FECHA -> "dd/mm/yyyy";
                    case FECHA_HORA -> "dd/mm/yyyy hh:mm";
                    case TEXTO -> "@";
                };
                estilo.setDataFormat(libro.createDataFormat().getFormat(formato));
                if (total) {
                    estilo.setFont(fuenteNegrita);
                }
                return estilo;
            });
        }
    }
}
