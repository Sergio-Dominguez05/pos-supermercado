package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.impuestos.dominio.CalculadoraIva;
import gt.supermercado.pos.impuestos.dominio.LineaCalculada;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.TipoTarjeta;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-08.05 Imprimir la factura y HU-12.08 Configurar las impresoras: el contenido del ticket y sus bytes ESC/POS. */
class TicketFacturaTest {

    private static final UUID AUTORIZACION = UUID.fromString("5b2c1f0e-1a2b-4c3d-9e8f-001122334455");
    private static final OffsetDateTime CERTIFICADA = OffsetDateTime.parse("2026-11-17T16:15:30Z");   // 10:15:30 GT

    @Test
    void llevaLosDatosQuePideLaSatParaLaRepresentacionImpresa() {   // crit. 1
        String texto = TicketFactura.armar(datos(List.of(pagoDebito("4321")), "Certificador S.A.", "12345678"), 80)
                .texto();

        assertThat(texto).contains("Súper del Valle, zona 1", "Supermercados del Valle, S.A.", "NIT 576937K",
                "6a. avenida 5-10, zona 1", "Guatemala, Guatemala", "Establecimiento 1",
                "FACTURA", "A1B2C3D4", "123456789", AUTORIZACION.toString().toUpperCase(),
                "17/11/2026 10:15:30",   // en la hora de Guatemala
                "CF", "Consumidor Final", "Arroz 1 lb", "3 x Q5.00", "Q15.00",
                "Sujeto a pagos trimestrales ISR", "Certificador: Certificador S.A.", "NIT certificador: 12345678",
                "Venta N.º 1042 · Caja 1");
    }

    @Test
    void losTotalesVanEnOrdenSubtotalIvaYTotal() {   // crit. 2 y HU-04.04 crit. 1
        String texto = TicketFactura.armar(datos(List.of(pagoDebito(null)), "Certificador", null), 80).texto();

        int subtotal = texto.indexOf("Subtotal");
        int iva = texto.indexOf("\nIVA");
        int total = texto.indexOf("\nTOTAL");
        assertThat(subtotal).isPositive().isLessThan(iva);
        assertThat(iva).isLessThan(total);
        assertThat(texto).containsPattern("Subtotal +Q13\\.39").containsPattern("IVA +Q1\\.61")
                .containsPattern("TOTAL +Q15\\.00");
    }

    @Test
    void incluyeElPagoConTarjetaSinElNumeroCompleto() {   // crit. 3
        String conUltimos4 = TicketFactura.armar(datos(List.of(pagoDebito("4321")), "C", null), 80).texto();
        String sinUltimos4 = TicketFactura.armar(datos(List.of(pagoDebito(null)), "C", null), 80).texto();

        assertThat(conUltimos4).contains("Tarjeta de débito ****4321", "Aut. 778899");
        assertThat(sinUltimos4).contains("Tarjeta de débito\n").doesNotContain("****");
        assertThat(sinUltimos4).doesNotContain("NIT certificador");   // sin NIT configurado no se inventa
    }

    @Test
    void laReimpresionLlevaLaLeyendaDeCopia() {   // HU-08.12 crit. 3
        DatosTicketFactura original = datos(List.of(pagoDebito("4321")), "C", null);
        DatosTicketFactura copia = new DatosTicketFactura(original.emisor(), original.establecimiento(),
                original.receptorId(), original.receptorNombre(), original.autorizacion(), original.serie(),
                original.numero(), original.certificadaEn(), original.emitidaEn(), original.venta(), original.pagos(),
                original.certificador(), original.nitCertificador(), true, null);

        assertThat(TicketFactura.armar(original, 80).texto()).doesNotContain("REIMPRESIÓN");
        String texto = TicketFactura.armar(copia, 58).texto();
        assertThat(texto.split(Pattern.quote(TicketFactura.LEYENDA_REIMPRESION), -1)).hasSize(3);   // inicio y final
        assertThat(texto.indexOf("COPIA")).isLessThan(texto.indexOf("FACTURA"));
    }

    @Test
    void elComprobanteEnContingenciaDiceQueEstaPendienteDeCertificar() {   // HU-08.07 crit. 1 y 2
        DatosTicketFactura original = datos(List.of(pagoDebito("4321")), "C", null);
        DatosTicketFactura contingencia = new DatosTicketFactura(original.emisor(), original.establecimiento(),
                original.receptorId(), original.receptorNombre(), null, null, null, null, original.emitidaEn(),
                original.venta(), original.pagos(), original.certificador(), null, false, 100000007L);

        String texto = TicketFactura.armar(contingencia, 58).texto();

        assertThat(texto).contains("COMPROBANTE EN", "CONTINGENCIA", "PENDIENTE DE CERTIFICAR", "100000007",
                "Subtotal", "TOTAL").doesNotContain("Autorización", "Serie");
        assertThat(TicketFactura.armar(contingencia, 80).renglones())
                .allSatisfy(renglon -> assertThat(renglon.texto().length()).isLessThanOrEqualTo(48));
    }

    @Test
    void enPapelDe58MmNingunRenglonPasaDe32Columnas() {   // HU-12.08 crit. 1
        Ticket ticket = TicketFactura.armar(datos(List.of(pagoDebito("4321")), "Certificador con un nombre largo S.A.",
                "12345678"), 58);

        assertThat(ticket.columnas()).isEqualTo(32);
        assertThat(ticket.renglones()).allSatisfy(renglon -> assertThat(renglon.texto().length()).isLessThanOrEqualTo(32));
        assertThat(Ticket.paraPapel(80).columnas()).isEqualTo(48);
    }

    @Test
    void cadaCopiaSaleConSuCorteYLasTildesEnLaPagina850() {   // HU-12.08 crit. 3
        Ticket ticket = Ticket.paraPapel(80).titulo("Número").texto("Ñandú");

        byte[] dos = EscPos.codificar(ticket, 2);
        String comoTexto = new String(dos, Charset.forName("IBM850"));

        assertThat(contar(dos, new byte[] {0x1D, 'V', 66, 0})).isEqualTo(2);
        assertThat(contar(dos, new byte[] {0x1B, 't', 2})).isEqualTo(2);
        assertThat(comoTexto).contains("Número", "Ñandú");
        assertThat(EscPos.codificar(ticket, 1)).hasSize(dos.length / 2);
    }

    @Test
    void laPaginaDePruebaMuestraElAnchoYLasTildes() {   // HU-12.08 crit. 2
        Impresora impresora = new Impresora(1, "Caja 1", "192.168.1.50", 9100, 58, 2, 1, null);

        Ticket pagina = PaginaDePrueba.armar(impresora, CERTIFICADA);

        assertThat(pagina.texto()).contains("PÁGINA DE PRUEBA", "192.168.1.50:9100", "58 mm (32 columnas)",
                "12345678901234567890123456789012", "áéíóú");
    }

    @Test
    void unTextoLargoSeParteEnRenglonesPorPalabras() {
        Ticket ticket = Ticket.paraPapel(58).texto("Detergente en polvo multiusos con aroma a limón 1 kg");

        assertThat(ticket.texto()).isEqualTo("Detergente en polvo multiusos\ncon aroma a limón 1 kg\n");
        // Si el dato y su valor no caben juntos, el valor baja al renglón siguiente, alineado a la derecha.
        assertThat(Ticket.paraPapel(80).dato("Certificación", "17/11/2026 10:15:30").texto())
                .isEqualTo("Certificación" + " ".repeat(16) + "17/11/2026 10:15:30\n");
        assertThat(Ticket.paraPapel(58).dato("Certificación", "17/11/2026 10:15:30").texto())
                .isEqualTo("Certificación\n" + " ".repeat(13) + "17/11/2026 10:15:30\n");
    }

    private static DatosTicketFactura datos(List<Pago> pagos, String certificador, String nitCertificador) {
        LineaCalculada arroz = CalculadoraIva.linea(new BigDecimal("5.00"), 3);
        Venta venta = new Venta(31, 1042, 1, 1, "Caja 1", 1, EstadoVenta.FINALIZADA, new BigDecimal("0.12"),
                OffsetDateTime.now(), null, List.of(new LineaVenta(1, 1, "7401", "Arroz 1 lb", 3,
                arroz.precioUnitario(), arroz.subtotalConIva(), arroz.base(), arroz.iva())));
        return new DatosTicketFactura(
                new Emisor("576937K", "Supermercados del Valle, S.A.", "Súper del Valle", "Zona 1", null, null,
                        AfiliacionIva.GEN, null),
                new Establecimiento(1, "1", "Súper del Valle, zona 1", "6a. avenida 5-10, zona 1", "01001",
                        "Guatemala", "Guatemala", null),
                "CF", "Consumidor Final", AUTORIZACION, "A1B2C3D4", "123456789", CERTIFICADA, CERTIFICADA, venta,
                pagos, certificador, nitCertificador, false, null);
    }

    private static Pago pagoDebito(String ultimos4) {
        return new Pago(1, 31, 1, 1, TipoTarjeta.DEBITO, "778899", ultimos4, new BigDecimal("15.00"),
                OffsetDateTime.now());
    }

    private static int contar(byte[] datos, byte[] patron) {
        int veces = 0;
        for (int i = 0; i <= datos.length - patron.length; i++) {
            boolean igual = true;
            for (int j = 0; j < patron.length && igual; j++) {
                igual = datos[i + j] == patron[j];
            }
            if (igual) {
                veces++;
            }
        }
        return veces;
    }
}
