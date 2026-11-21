package gt.supermercado.pos.catalogo.infraestructura;

import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo;
import gt.supermercado.pos.catalogo.dominio.ArchivoCatalogo.Formato;
import gt.supermercado.pos.catalogo.dominio.Producto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HU-03.12: el catálogo en Excel o CSV con las columnas de {@link ArchivoCatalogo}. En Excel el precio es un número y
 * la fecha una fecha; el CSV va en UTF-8 con BOM (para que Excel lea las tildes), separado por comas y con el precio
 * con punto decimal. Un texto que empieza con {@code =}, {@code +}, {@code -} o {@code @} lleva un apóstrofo delante
 * para que la hoja de cálculo no lo ejecute como fórmula.
 */
@Component
class ArchivoCatalogoPoi implements ArchivoCatalogo.Escritor {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public byte[] exportar(List<Producto> productos, Formato formato) {
        return formato == Formato.CSV ? csv(productos) : excel(productos);
    }

    /** HU-03.05 crit. 1: los títulos de las tres columnas; en Excel, la del código con formato de texto. */
    @Override
    public byte[] plantilla(Formato formato) {
        if (formato == Formato.CSV) {
            StringBuilder texto = new StringBuilder("﻿");
            linea(texto, ArchivoCatalogo.COLUMNAS_PLANTILLA);
            return texto.toString().getBytes(StandardCharsets.UTF_8);
        }
        try (SXSSFWorkbook libro = new SXSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Catálogo");
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(libro.createDataFormat().getFormat("@"));
            hoja.setDefaultColumnStyle(0, texto);
            Row titulos = hoja.createRow(0);
            for (int i = 0; i < ArchivoCatalogo.COLUMNAS_PLANTILLA.size(); i++) {
                Cell celda = titulos.createCell(i);
                celda.setCellValue(ArchivoCatalogo.COLUMNAS_PLANTILLA.get(i));
                celda.setCellStyle(negrita);
            }
            hoja.createFreezePane(0, 1);
            libro.write(salida);
            libro.dispose();
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo generar la plantilla del catálogo", error);
        }
    }

    static String estado(Producto producto) {
        return producto.activo() ? "Activo" : "Inactivo";
    }

    static String seguro(String valor) {
        if (valor == null) {
            return "";
        }
        return !valor.isEmpty() && "=+-@".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
    }

    private static byte[] csv(List<Producto> productos) {
        StringBuilder texto = new StringBuilder("﻿");
        linea(texto, ArchivoCatalogo.COLUMNAS_EXPORTACION);
        for (Producto producto : productos) {
            linea(texto, List.of(seguro(producto.codigoBarras()), seguro(producto.descripcion()),
                    producto.precio().setScale(2).toPlainString(), estado(producto),
                    producto.modificadoEn() == null ? ""
                            : FECHA_HORA.format(producto.modificadoEn().atZoneSameInstant(GUATEMALA))));
        }
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void linea(StringBuilder texto, List<String> celdas) {
        for (int i = 0; i < celdas.size(); i++) {
            if (i > 0) {
                texto.append(',');
            }
            String celda = celdas.get(i);
            if (celda.contains(",") || celda.contains("\"") || celda.contains("\n") || celda.contains("\r")) {
                texto.append('"').append(celda.replace("\"", "\"\"")).append('"');
            } else {
                texto.append(celda);
            }
        }
        texto.append("\r\n");
    }

    private static byte[] excel(List<Producto> productos) {
        try (SXSSFWorkbook libro = new SXSSFWorkbook(200); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Catálogo");
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);
            CellStyle dinero = libro.createCellStyle();
            dinero.setDataFormat(libro.createDataFormat().getFormat("0.00"));
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            CellStyle texto = libro.createCellStyle();
            texto.setDataFormat(libro.createDataFormat().getFormat("@"));
            Row titulos = hoja.createRow(0);
            for (int i = 0; i < ArchivoCatalogo.COLUMNAS_EXPORTACION.size(); i++) {
                Cell celda = titulos.createCell(i);
                celda.setCellValue(ArchivoCatalogo.COLUMNAS_EXPORTACION.get(i));
                celda.setCellStyle(negrita);
            }
            hoja.createFreezePane(0, 1);
            int fila = 1;
            for (Producto producto : productos) {
                Row renglon = hoja.createRow(fila++);
                // El código va como texto para que Excel no le quite los ceros de la izquierda.
                Cell codigo = renglon.createCell(0);
                codigo.setCellValue(seguro(producto.codigoBarras()));
                codigo.setCellStyle(texto);
                renglon.createCell(1).setCellValue(seguro(producto.descripcion()));
                Cell precio = renglon.createCell(2);
                precio.setCellValue(producto.precio().doubleValue());
                precio.setCellStyle(dinero);
                renglon.createCell(3).setCellValue(estado(producto));
                if (producto.modificadoEn() != null) {
                    Cell modificado = renglon.createCell(4);
                    modificado.setCellValue(producto.modificadoEn().atZoneSameInstant(GUATEMALA).toLocalDateTime());
                    modificado.setCellStyle(fecha);
                }
            }
            libro.write(salida);
            libro.dispose();
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo generar el Excel del catálogo", error);
        }
    }
}
