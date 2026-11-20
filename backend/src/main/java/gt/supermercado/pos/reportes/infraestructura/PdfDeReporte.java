package gt.supermercado.pos.reportes.infraestructura;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import gt.supermercado.pos.reportes.dominio.FiltroAplicado;
import gt.supermercado.pos.reportes.dominio.TablaReporte;
import gt.supermercado.pos.reportes.dominio.TablaReporte.Columna;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * HU-10.13: el reporte en PDF. Crit. 2: nombre del reporte, datos de la empresa, filtros aplicados, fecha y hora de
 * generación y usuario. Crit. 3: las filas y los totales en el mismo orden que la pantalla. Las tablas anchas van en
 * hoja horizontal; el encabezado de la tabla se repite en cada página.
 */
final class PdfDeReporte {

    private static final Color GRIS = new Color(235, 238, 242);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font EMPRESA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

    private PdfDeReporte() {
    }

    static byte[] generar(TablaReporte tabla) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Rectangle pagina = tabla.columnas().size() > 6 ? PageSize.LETTER.rotate() : PageSize.LETTER;
        Document documento = new Document(pagina, 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.addTitle(tabla.encabezado().titulo());
        documento.addCreator("POS Supermercado");
        documento.open();

        List<String> empresa = tabla.empresa().lineas();
        documento.add(new Paragraph(empresa.getFirst(), EMPRESA));
        empresa.stream().skip(1).forEach(linea -> documento.add(new Paragraph(linea, NORMAL)));

        Paragraph titulo = new Paragraph(tabla.encabezado().titulo(), TITULO);
        titulo.setSpacingBefore(10);
        titulo.setSpacingAfter(4);
        documento.add(titulo);
        for (FiltroAplicado filtro : tabla.encabezado().filtros()) {
            Paragraph linea = new Paragraph();
            linea.add(new Phrase(filtro.etiqueta() + ": ", NEGRITA));
            linea.add(new Phrase(filtro.valor(), NORMAL));
            documento.add(linea);
        }
        documento.add(new Paragraph("Generado el %s por %s".formatted(
                TextosDeCelda.FECHA_HORA.format(tabla.encabezado().generadoEn().atZoneSameInstant(TextosDeCelda.GUATEMALA)),
                tabla.encabezado().generadoPor()), PEQUENA));

        if (!tabla.resumen().isEmpty()) {
            Paragraph resumen = new Paragraph();
            resumen.setSpacingBefore(8);
            for (TablaReporte.Dato dato : tabla.resumen()) {
                resumen.add(new Phrase(dato.etiqueta() + ": ", NEGRITA));
                resumen.add(new Phrase(TextosDeCelda.texto(dato.valor(), dato.tipo()) + "    ", NORMAL));
            }
            documento.add(resumen);
        }

        if (tabla.filas().isEmpty() && tabla.pie().isEmpty()) {
            Paragraph vacio = new Paragraph("No hay datos con estos filtros.", NORMAL);
            vacio.setSpacingBefore(12);
            documento.add(vacio);
        } else {
            documento.add(tablaDe(tabla));
        }

        for (String nota : tabla.notas()) {
            Paragraph parrafo = new Paragraph(nota, PEQUENA);
            parrafo.setSpacingBefore(6);
            documento.add(parrafo);
        }
        documento.close();
        return salida.toByteArray();
    }

    private static PdfPTable tablaDe(TablaReporte tabla) {
        List<Columna> columnas = tabla.columnas();
        PdfPTable pdf = new PdfPTable(columnas.size());
        pdf.setWidthPercentage(100);
        pdf.setSpacingBefore(12);
        pdf.setHeaderRows(1);
        for (Columna columna : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(columna.titulo(), NEGRITA));
            celda.setBackgroundColor(GRIS);
            celda.setPadding(4);
            celda.setHorizontalAlignment(TextosDeCelda.esNumero(columna.tipo()) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            pdf.addCell(celda);
        }
        tabla.filas().forEach(fila -> agregarFila(pdf, columnas, fila, NORMAL, null));
        tabla.pie().forEach(fila -> agregarFila(pdf, columnas, fila, NEGRITA, GRIS));
        return pdf;
    }

    private static void agregarFila(PdfPTable pdf, List<Columna> columnas, List<Object> fila, Font fuente, Color fondo) {
        for (int i = 0; i < columnas.size(); i++) {
            Columna columna = columnas.get(i);
            PdfPCell celda = new PdfPCell(new Phrase(TextosDeCelda.texto(fila.get(i), columna.tipo()), fuente));
            celda.setPadding(4);
            celda.setHorizontalAlignment(TextosDeCelda.esNumero(columna.tipo()) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            if (fondo != null) {
                celda.setBackgroundColor(fondo);
            }
            pdf.addCell(celda);
        }
    }
}
