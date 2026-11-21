package gt.supermercado.pos.bitacora.infraestructura;

import gt.supermercado.pos.bitacora.dominio.ExportadorBitacora;
import gt.supermercado.pos.bitacora.dominio.FormatoBitacora;
import gt.supermercado.pos.bitacora.dominio.RegistroConsultado;
import gt.supermercado.pos.bitacora.dominio.RegistroExportado;
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
import java.util.ArrayList;
import java.util.List;

/**
 * HU-11.09 crit. 1: la bitácora en Excel (.xlsx) o CSV, con las mismas columnas. El Excel se escribe por partes
 * ({@link SXSSFWorkbook}) para no cargar en memoria una exportación grande; el CSV va en UTF-8 con BOM para que Excel
 * lea bien las tildes.
 *
 * <p>Un texto que empieza con {@code =}, {@code +}, {@code -} o {@code @} se escribe con un apóstrofo delante, para que
 * la hoja de cálculo no lo ejecute como fórmula (por ejemplo, el nombre de un producto o un motivo escrito a mano).
 */
@Component
class ExportadorBitacoraArchivo implements ExportadorBitacora {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public byte[] exportar(List<RegistroExportado> registros, FormatoBitacora formato) {
        return formato == FormatoBitacora.CSV ? csv(registros) : excel(registros);
    }

    /** Las celdas de un registro, en el orden de {@link ExportadorBitacora#COLUMNAS}. */
    static List<String> celdas(RegistroExportado exportado) {
        RegistroConsultado r = exportado.registro();
        List<String> celdas = new ArrayList<>();
        celdas.add(String.valueOf(r.secuencia()));
        celdas.add(r.ocurridoEn() == null ? "" : FECHA_HORA.format(r.ocurridoEn().atZoneSameInstant(GUATEMALA)));
        celdas.add(r.usuario());
        celdas.add(r.rol());
        celdas.add(r.caja());
        celdas.add(r.modulo());
        celdas.add(r.accion());
        celdas.add(r.exitoso() ? "Exitoso" : "Fallido");
        celdas.add(r.descripcion());
        celdas.add(exportado.entidad());
        celdas.add(exportado.entidadId() == null ? null : String.valueOf(exportado.entidadId()));
        celdas.add(exportado.cambios());
        return celdas.stream().map(ExportadorBitacoraArchivo::seguro).toList();
    }

    /** Evita que una hoja de cálculo interprete el texto como fórmula. */
    static String seguro(String valor) {
        if (valor == null) {
            return "";
        }
        return !valor.isEmpty() && "=+-@".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
    }

    private static byte[] csv(List<RegistroExportado> registros) {
        StringBuilder texto = new StringBuilder("﻿");
        linea(texto, ExportadorBitacora.COLUMNAS);
        registros.forEach(registro -> linea(texto, celdas(registro)));
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

    private static byte[] excel(List<RegistroExportado> registros) {
        try (SXSSFWorkbook libro = new SXSSFWorkbook(200); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Bitácora");
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);
            Row titulos = hoja.createRow(0);
            for (int i = 0; i < ExportadorBitacora.COLUMNAS.size(); i++) {
                Cell celda = titulos.createCell(i);
                celda.setCellValue(ExportadorBitacora.COLUMNAS.get(i));
                celda.setCellStyle(negrita);
            }
            hoja.createFreezePane(0, 1);
            int fila = 1;
            for (RegistroExportado registro : registros) {
                Row renglon = hoja.createRow(fila++);
                List<String> celdas = celdas(registro);
                renglon.createCell(0).setCellValue(registro.registro().secuencia());
                for (int i = 1; i < celdas.size(); i++) {
                    renglon.createCell(i).setCellValue(celdas.get(i));
                }
            }
            libro.write(salida);
            libro.dispose();
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo generar el Excel de la bitácora", error);
        }
    }
}
