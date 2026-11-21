package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.configuracion.dominio.FormatoComprobante;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import gt.supermercado.pos.impuestos.dominio.Totales;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HU-08.05 Imprimir la factura: la representación impresa del DTE certificado.
 * <ul>
 *   <li>Crit. 1: emisor, receptor, autorización, serie, número, fecha de certificación, detalle de productos, totales
 *       y datos del certificador.</li>
 *   <li>Crit. 2 y HU-04.04 crit. 1: los totales en el orden Subtotal, IVA y TOTAL, con los montos del servidor.</li>
 *   <li>Crit. 3: cada pago con tarjeta: tipo, últimos 4 dígitos (si se registraron) y autorización.</li>
 * </ul>
 * Crit. 4 (con HU-12.09): el logo y los mensajes configurados ({@link FormatoComprobante}) solo se agregan: el logo al
 * principio, el encabezado debajo de los datos del emisor y el pie al final. Los datos de la SAT y el bloque de
 * totales se arman aquí siempre, sin importar el formato. Fuera de esta entrega: el código QR (crit. 5), que la SAT no
 * exige y depende del certificador que se elija.
 */
public final class TicketFactura {

    /** Las fechas se imprimen en la hora de Guatemala, sin importar la zona del servidor. */
    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    /** La frase del régimen General (frase 1, escenario 1), la misma del DTE (HU-08.02). */
    static final String FRASE_ISR = "Sujeto a pagos trimestrales ISR";
    /** HU-08.12 crit. 3: la leyenda de una reimpresión, al principio y al final del ticket. */
    public static final String LEYENDA_REIMPRESION = "*** COPIA - REIMPRESIÓN ***";
    /** HU-08.07 crit. 2: el comprobante en contingencia dice claramente que está pendiente de certificar. */
    public static final String LEYENDA_CONTINGENCIA = "PENDIENTE DE CERTIFICAR ANTE LA SAT";

    private TicketFactura() {
    }

    /** El ticket con el formato de siempre (sin logo ni encabezado). */
    public static Ticket armar(DatosTicketFactura datos, int anchoMm) {
        return armar(datos, anchoMm, FormatoComprobante.POR_OMISION);
    }

    /** HU-12.09: el ticket con el logo y los mensajes configurados. */
    public static Ticket armar(DatosTicketFactura datos, int anchoMm, FormatoComprobante formato) {
        Ticket ticket = Ticket.paraPapel(anchoMm).conLogo(formato.logo());
        Establecimiento establecimiento = datos.establecimiento();
        if (datos.reimpresion()) {
            ticket.centradoNegrita(LEYENDA_REIMPRESION);
        }
        ticket.titulo(establecimiento.nombre())
                .centrado(datos.emisor().razonSocial())
                .centrado("NIT " + datos.emisor().nit())
                .centrado(establecimiento.direccion())
                .centrado(establecimiento.municipio() + ", " + establecimiento.departamento())
                .centrado("Establecimiento " + establecimiento.codigoSat());
        if (formato.encabezado() != null) {
            ticket.centrado(formato.encabezado());
        }
        ticket.separador();
        if (datos.enContingencia()) {
            ticket.titulo("COMPROBANTE EN CONTINGENCIA")
                    .centradoNegrita(LEYENDA_CONTINGENCIA)
                    .dato("Número de acceso", String.valueOf(datos.numeroAcceso()))
                    .dato("Emisión", fecha(datos.emitidaEn()))
                    .texto("La factura se certificará en cuanto vuelva la conexión con el certificador.");
        } else {
            ticket.centradoNegrita("FACTURA")
                    .centrado("Documento Tributario Electrónico")
                    .dato("Serie", datos.serie())
                    .dato("Número", datos.numero())
                    .texto("Autorización:")
                    .texto(datos.autorizacion().toString().toUpperCase())
                    .dato("Certificación", fecha(datos.certificadaEn()))
                    .dato("Emisión", fecha(datos.emitidaEn()));
        }
        ticket.separador()
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
        ticket.centrado("Venta N.º %d · %s".formatted(datos.venta().numero(), datos.venta().cajaNombre()));
        if (formato.pie() != null) {
            ticket.centrado(formato.pie());
        }
        if (datos.reimpresion()) {
            ticket.centradoNegrita(LEYENDA_REIMPRESION);
        }
        return ticket;
    }

    /**
     * HU-12.09 crit. 3: una factura de ejemplo con el emisor y el establecimiento configurados (o de muestra si
     * todavía no lo están), para ver el formato antes de guardarlo. No corresponde a ninguna venta real.
     */
    public static Ticket ejemplo(Emisor emisor, Establecimiento establecimiento, int anchoMm,
                                 FormatoComprobante formato) {
        Emisor elEmisor = emisor != null ? emisor : new Emisor("CF", "Su supermercado, S.A.", "Su supermercado",
                "Ciudad", null, null, null, null);
        Establecimiento elEstablecimiento = establecimiento != null ? establecimiento : new Establecimiento(1, "1",
                "Su supermercado", "Dirección del establecimiento", null, "Guatemala", "Guatemala", null);
        OffsetDateTime ahora = OffsetDateTime.now(GUATEMALA);
        LineaVenta arroz = new LineaVenta(1, 1, "7401", "Arroz 1 lb (ejemplo)", 2, new BigDecimal("5.00"),
                new BigDecimal("10.00"), new BigDecimal("8.928571"), new BigDecimal("1.071429"));
        Venta venta = new Venta(0, 1, 0, 0, "Caja 1", 0, EstadoVenta.FINALIZADA, new BigDecimal("0.12"), ahora, null,
                List.of(arroz));
        Pago pago = new Pago(0, 0, 0, 0, TipoTarjeta.DEBITO, "000000", "0000", new BigDecimal("10.00"), ahora);
        return armar(new DatosTicketFactura(elEmisor, elEstablecimiento, "CF", "Consumidor Final",
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"), "EJEMPLO", "1", ahora, ahora,
                venta, List.of(pago), "Certificador", null, false, null), anchoMm, formato);
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
