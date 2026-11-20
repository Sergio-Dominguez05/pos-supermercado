package gt.supermercado.pos.impresion.infraestructura;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import gt.supermercado.pos.facturacion.dominio.RepresentacionGrafica;
import gt.supermercado.pos.impresion.aplicacion.ImpresionFacturas;
import gt.supermercado.pos.impresion.dominio.Ticket;
import gt.supermercado.pos.pagos.aplicacion.GestionPagos;
import gt.supermercado.pos.ventas.aplicacion.GestionVentas;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * HU-08.15 crit. 2: la representación gráfica de la factura en PDF. Es el mismo ticket de 80 mm que se imprime
 * (HU-08.05), con letra de ancho fijo para que los montos queden alineados como en el papel.
 */
@Component
class RepresentacionGraficaPdf implements RepresentacionGrafica {

    private static final float TAMANO = 9f;
    private static final float INTERLINEADO = 11.5f;
    private static final float MARGEN = 24f;
    private static final Font NORMAL = FontFactory.getFont(FontFactory.COURIER, TAMANO);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.COURIER_BOLD, TAMANO);
    private static final Font DESTACADA = FontFactory.getFont(FontFactory.COURIER_BOLD, TAMANO * 1.5f);

    private final ImpresionFacturas impresion;
    private final GestionVentas ventas;
    private final GestionPagos pagos;

    RepresentacionGraficaPdf(ImpresionFacturas impresion, GestionVentas ventas, GestionPagos pagos) {
        this.impresion = impresion;
        this.ventas = ventas;
        this.pagos = pagos;
    }

    @Override
    public byte[] pdf(long ventaId) {
        Venta venta = ventas.porId(ventaId);
        Ticket ticket = impresion.ticket(venta, pagos.deLaVenta(ventaId), 80, false);
        // Courier: cada carácter mide 0.6 veces el tamaño de la letra.
        float ancho = ticket.columnas() * TAMANO * 0.6f + 2 * MARGEN;
        float alto = ticket.renglones().size() * INTERLINEADO * 1.2f + 2 * MARGEN;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(new Rectangle(ancho, alto), MARGEN, MARGEN, MARGEN, MARGEN);
        PdfWriter.getInstance(documento, salida);
        documento.addTitle("Factura electrónica");
        documento.open();
        for (Ticket.Renglon renglon : ticket.renglones()) {
            Font letra = renglon.destacado() ? DESTACADA : renglon.negrita() ? NEGRITA : NORMAL;
            Paragraph linea = new Paragraph(renglon.destacado() ? INTERLINEADO * 1.5f : INTERLINEADO,
                    renglon.texto().isEmpty() ? " " : renglon.texto(), letra);
            linea.setAlignment(renglon.alineacion() == Ticket.Alineacion.CENTRO ? Element.ALIGN_CENTER
                    : Element.ALIGN_LEFT);
            documento.add(linea);
        }
        documento.close();
        return salida.toByteArray();
    }
}
