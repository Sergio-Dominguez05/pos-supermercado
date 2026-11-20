package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.caja.dominio.CierreDeTurno;
import gt.supermercado.pos.caja.dominio.ComprobanteDeCierre;
import gt.supermercado.pos.caja.dominio.ResumenDeTurno;

/**
 * HU-09.10 Imprimir el comprobante de cierre.
 * <ul>
 *   <li>Crit. 1: caja, cajero, apertura y cierre, cantidad de ventas, total por tipo de tarjeta, facturas anuladas,
 *       total registrado, total del cierre de lote, diferencia y justificación.</li>
 *   <li>Crit. 2: espacio para las firmas del cajero y del supervisor.</li>
 *   <li>Crit. 3: la reimpresión lleva la leyenda de copia.</li>
 * </ul>
 */
public final class TicketCierre {

    private TicketCierre() {
    }

    public static Ticket armar(ComprobanteDeCierre comprobante, int anchoMm, boolean reimpresion) {
        CierreDeTurno cierre = comprobante.cierre();
        ResumenDeTurno resumen = comprobante.resumen();
        Ticket ticket = Ticket.paraPapel(anchoMm);
        if (reimpresion) {
            ticket.centradoNegrita(TicketFactura.LEYENDA_REIMPRESION);
        }
        ticket.titulo("CIERRE DE CAJA")
                .centrado(cierre.cajaNombre())
                .separador()
                .dato("Cajero", cierre.usuarioNombre())
                .dato("Apertura", TicketFactura.fecha(cierre.abiertoEn()))
                .dato("Cierre", TicketFactura.fecha(cierre.cerradoEn()))
                .separador()
                .dato("Ventas", String.valueOf(resumen.ventas()))
                .dato("Tarjeta de débito", TicketFactura.quetzales(resumen.debito()))
                .dato("Tarjeta de crédito", TicketFactura.quetzales(resumen.credito()))
                .dato("Facturas anuladas (" + resumen.anuladas() + ")", TicketFactura.quetzales(resumen.totalAnulado()))
                .separador()
                .dato("Total registrado", TicketFactura.quetzales(cierre.totalRegistrado()))
                .dato("Cierre de lote", TicketFactura.quetzales(cierre.totalLote()))
                .datoNegrita("DIFERENCIA", TicketFactura.quetzales(cierre.diferencia()));
        if (cierre.justificacion() != null) {
            ticket.texto("Justificación: " + cierre.justificacion());
        }
        if (resumen.contingencias() > 0) {
            ticket.texto("Comprobantes en contingencia: %d (pendientes de certificar)".formatted(resumen.contingencias()));
        }
        String linea = "_".repeat(Math.min(28, ticket.columnas()));
        ticket.separador()
                .texto(" ")
                .texto(" ")
                .centrado(linea)
                .centrado("Firma del cajero")
                .texto(" ")
                .texto(" ")
                .centrado(linea)
                .centrado("Firma del supervisor");
        if (reimpresion) {
            ticket.centradoNegrita(TicketFactura.LEYENDA_REIMPRESION);
        }
        return ticket;
    }
}
