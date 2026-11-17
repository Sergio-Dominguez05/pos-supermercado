package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.impuestos.dominio.Totales;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * HU-08.05 Imprimir la factura: la representación impresa del DTE certificado.
 * <ul>
 *   <li>Crit. 1: emisor, receptor, autorización, serie, número, fecha de certificación, detalle de productos, totales
 *       y datos del certificador.</li>
 *   <li>Crit. 2 y HU-04.04 crit. 1: los totales en el orden Subtotal, IVA y TOTAL, con los montos del servidor.</li>
 *   <li>Crit. 3: cada pago con tarjeta: tipo, últimos 4 dígitos (si se registraron) y autorización.</li>
 * </ul>
 * Fuera de esta entrega: el logo y los mensajes configurables (crit. 4, EP-12) y el código QR (crit. 5), que la SAT no
 * exige y depende del certificador que se elija.
 */
public final class TicketFactura {

    /** Las fechas se imprimen en la hora de Guatemala, sin importar la zona del servidor. */
    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    /** La frase del régimen General (frase 1, escenario 1), la misma del DTE (HU-08.02). */
    static final String FRASE_ISR = "Sujeto a pagos trimestrales ISR";

    private TicketFactura() {
    }

    public static Ticket armar(DatosTicketFactura datos, int anchoMm) {
        Ticket ticket = Ticket.paraPapel(anchoMm);
        Establecimiento establecimiento = datos.establecimiento();
        ticket.titulo(establecimiento.nombre())
                .centrado(datos.emisor().razonSocial())
                .centrado("NIT " + datos.emisor().nit())
                .centrado(establecimiento.direccion())
                .centrado(establecimiento.municipio() + ", " + establecimiento.departamento())
                .centrado("Establecimiento " + establecimiento.codigoSat())
                .separador()
                .centradoNegrita("FACTURA")
                .centrado("Documento Tributario Electrónico")
                .dato("Serie", datos.serie())
                .dato("Número", datos.numero())
                .texto("Autorización:")
                .texto(datos.autorizacion().toString().toUpperCase())
                .dato("Certificación", fecha(datos.certificadaEn()))
                .dato("Emisión", fecha(datos.emitidaEn()))
                .separador()
                .dato("NIT", datos.receptorId())
                .texto("Nombre: " + datos.receptorNombre())
                .separador();
        for (LineaVenta linea : datos.venta().lineas()) {
            ticket.texto(linea.descripcion())
                    .dato("  %d x %s".formatted(linea.cantidad(), quetzales(linea.precioUnitario())),
                            quetzales(linea.subtotalConIva()));
        }
        Totales totales = datos.venta().totales();
        ticket.separador()
                .dato("Subtotal", quetzales(totales.subtotal()))
                .dato("IVA", quetzales(totales.iva()))
                .datoNegrita("TOTAL", quetzales(totales.total()))
                .separador();
        for (Pago pago : datos.pagos()) {
            ticket.texto(tarjeta(pago))
                    .dato("  Aut. " + pago.autorizacion(), quetzales(pago.monto()));
        }
        ticket.separador()
                .centrado(FRASE_ISR)
                .centrado("Certificador: " + datos.certificador());
        if (datos.nitCertificador() != null) {
            ticket.centrado("NIT certificador: " + datos.nitCertificador());
        }
        return ticket.centrado("Venta N.º %d · %s".formatted(datos.venta().numero(), datos.venta().cajaNombre()))
                .centrado("¡Gracias por su compra!");
    }

    /** "Tarjeta de débito ****4321": el número de la tarjeta nunca se guarda completo (HU-07.03 crit. 4). */
    static String tarjeta(Pago pago) {
        return "Tarjeta de " + (pago.tipoTarjeta() == TipoTarjeta.DEBITO ? "débito" : "crédito")
                + (pago.ultimos4() == null ? "" : " ****" + pago.ultimos4());
    }

    static String quetzales(BigDecimal monto) {
        return "Q" + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String fecha(OffsetDateTime fecha) {
        return FECHA.format(fecha.atZoneSameInstant(GUATEMALA));
    }
}
