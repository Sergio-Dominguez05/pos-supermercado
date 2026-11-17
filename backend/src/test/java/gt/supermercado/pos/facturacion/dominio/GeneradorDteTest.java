package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.impuestos.dominio.CalculadoraIva;
import gt.supermercado.pos.impuestos.dominio.LineaCalculada;
import gt.supermercado.pos.ventas.dominio.EstadoVenta;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-08.02 Generar el documento de la factura y HU-04.09 consistencia de montos con el DTE. */
class GeneradorDteTest {

    private static final Emisor EMISOR = new Emisor("576937K", "Supermercados del Valle, S.A.", "Súper del Valle",
            "5a. avenida 10-20, zona 1", "2222-3333", "facturas@delvalle.com", AfiliacionIva.GEN, null);
    private static final Establecimiento ESTABLECIMIENTO = new Establecimiento(1, "1", "Súper del Valle & Cía, zona 1",
            "6a. avenida 5-10, zona 1", "01001", "Guatemala", "Guatemala", null);
    private static final OffsetDateTime EMITIDA = OffsetDateTime.of(2026, 11, 17, 10, 30, 15, 123_000_000,
            ZoneOffset.ofHours(-6));

    @Test
    void elDocumentoEsUnaFacturaEnQuetzalesConElEsquemaFel() {   // crit. 2 y 4
        Document dte = leer(GeneradorDte.generar(datos(Receptor.consumidorFinal(), linea("Arroz 1 lb", "5.00", 3))));

        Element raiz = dte.getDocumentElement();
        assertThat(raiz.getNamespaceURI()).isEqualTo("http://www.sat.gob.gt/dte/fel/0.2.0");
        assertThat(raiz.getLocalName()).isEqualTo("GTDocumento");
        assertThat(raiz.getAttribute("Version")).isEqualTo("0.1");
        Element generales = unico(dte, "DatosGenerales");
        assertThat(generales.getAttribute("Tipo")).isEqualTo("FACT");
        assertThat(generales.getAttribute("CodigoMoneda")).isEqualTo("GTQ");
        assertThat(generales.getAttribute("FechaHoraEmision")).isEqualTo("2026-11-17T10:30:15-06:00");
    }

    @Test
    void incluyeElEmisorConSuEstablecimientoYLaFraseDelRegimenGeneral() {   // crit. 1
        Document dte = leer(GeneradorDte.generar(datos(Receptor.consumidorFinal(), linea("Arroz 1 lb", "5.00", 1))));

        Element emisor = unico(dte, "Emisor");
        assertThat(emisor.getAttribute("NITEmisor")).isEqualTo("576937K");
        assertThat(emisor.getAttribute("NombreEmisor")).isEqualTo("Supermercados del Valle, S.A.");
        assertThat(emisor.getAttribute("CodigoEstablecimiento")).isEqualTo("1");
        assertThat(emisor.getAttribute("NombreComercial")).isEqualTo("Súper del Valle & Cía, zona 1");
        assertThat(emisor.getAttribute("AfiliacionIVA")).isEqualTo("GEN");
        assertThat(texto(dte, "CodigoPostal")).isEqualTo("01001");
        assertThat(texto(dte, "Departamento")).isEqualTo("Guatemala");
        assertThat(texto(dte, "Pais")).isEqualTo("GT");
        Element frase = unico(dte, "Frase");
        assertThat(frase.getAttribute("TipoFrase")).isEqualTo("1");
        assertThat(frase.getAttribute("CodigoEscenario")).isEqualTo("1");
    }

    @Test
    void elReceptorPuedeSerConsumidorFinalNitOCui() {   // crit. 1
        Element cf = unico(leer(GeneradorDte.generar(datos(Receptor.consumidorFinal(), linea("A", "1.00", 1)))),
                "Receptor");
        assertThat(cf.getAttribute("IDReceptor")).isEqualTo("CF");
        assertThat(cf.getAttribute("NombreReceptor")).isEqualTo("Consumidor Final");
        assertThat(cf.hasAttribute("TipoEspecial")).isFalse();

        Element nit = unico(leer(GeneradorDte.generar(datos(new Receptor(Receptor.TipoReceptor.NIT, "1234560",
                "Comercial Pérez"), linea("A", "1.00", 1)))), "Receptor");
        assertThat(nit.getAttribute("IDReceptor")).isEqualTo("1234560");
        assertThat(nit.hasAttribute("TipoEspecial")).isFalse();

        Element cui = unico(leer(GeneradorDte.generar(datos(new Receptor(Receptor.TipoReceptor.CUI, "2456789010101",
                "Ana López"), linea("A", "1.00", 1)))), "Receptor");
        assertThat(cui.getAttribute("IDReceptor")).isEqualTo("2456789010101");
        assertThat(cui.getAttribute("TipoEspecial")).isEqualTo("CUI");
    }

    @Test
    void cadaLineaLlevaCantidadDescripcionPrecioBaseEIvaConSeisDecimales() {   // crit. 1 y 3
        Document dte = leer(GeneradorDte.generar(datos(Receptor.consumidorFinal(), linea("Arroz 1 lb", "5.00", 3))));

        Element item = unico(dte, "Item");
        assertThat(item.getAttribute("NumeroLinea")).isEqualTo("1");
        assertThat(item.getAttribute("BienOServicio")).isEqualTo("B");
        assertThat(texto(item, "Cantidad")).isEqualTo("3");
        assertThat(texto(item, "Descripcion")).isEqualTo("Arroz 1 lb");
        assertThat(texto(item, "PrecioUnitario")).isEqualTo("5.00");
        assertThat(texto(item, "Precio")).isEqualTo("15.000000");
        assertThat(texto(item, "MontoGravable")).isEqualTo("13.392857");
        assertThat(texto(item, "MontoImpuesto")).isEqualTo("1.607143");
        assertThat(texto(item, "Total")).isEqualTo("15.000000");
        assertThat(unico(dte, "TotalImpuesto").getAttribute("TotalMontoImpuesto")).isEqualTo("1.61");
        assertThat(texto(dte, "GranTotal")).isEqualTo("15.00");
    }

    /**
     * HU-04.09 crit. 1 y 3: con muchas líneas, cantidades altas y precios con centavos, cada monto del DTE es el que
     * guardó el POS, y los totales del DTE son los de la venta.
     */
    @Test
    void conCasosRealesLosMontosDelDteSonLosDelPos() {
        List<LineaVenta> lineas = new ArrayList<>();
        String[] precios = {"0.01", "0.99", "1.05", "3.33", "5.00", "7.77", "12.49", "19.99", "45.50", "99.99",
                "149.95", "0.37", "2.18", "8.88", "11.11"};
        int[] cantidades = {1, 7, 13, 50, 9999, 3, 2, 120, 1, 37, 4, 999, 21, 6, 250};
        for (int i = 0; i < 45; i++) {
            lineas.add(linea("Producto " + i, precios[i % precios.length], cantidades[(i * 7) % cantidades.length]));
        }
        Venta venta = venta(lineas);

        Document dte = leer(GeneradorDte.generar(new DatosDte(EMISOR, ESTABLECIMIENTO, Receptor.consumidorFinal(),
                EMITIDA, venta)));

        NodeList items = dte.getElementsByTagNameNS(GeneradorDte.ESPACIO_FEL, "Item");
        assertThat(items.getLength()).isEqualTo(45);
        BigDecimal sumaTotales = BigDecimal.ZERO;
        BigDecimal sumaIva = BigDecimal.ZERO;
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            LineaVenta linea = lineas.get(i);
            assertThat(new BigDecimal(texto(item, "MontoGravable"))).isEqualTo(linea.baseImponible());
            assertThat(new BigDecimal(texto(item, "MontoImpuesto"))).isEqualTo(linea.iva());
            assertThat(new BigDecimal(texto(item, "Total"))).isEqualTo(linea.subtotalConIva());
            assertThat(new BigDecimal(texto(item, "MontoGravable")).add(new BigDecimal(texto(item, "MontoImpuesto"))))
                    .isEqualByComparingTo(new BigDecimal(texto(item, "Total")));
            sumaTotales = sumaTotales.add(new BigDecimal(texto(item, "Total")));
            sumaIva = sumaIva.add(new BigDecimal(texto(item, "MontoImpuesto")));
        }
        assertThat(new BigDecimal(texto(dte, "GranTotal"))).isEqualTo(venta.totales().total())
                .isEqualTo(CalculadoraIva.redondearTotal(sumaTotales));
        assertThat(new BigDecimal(unico(dte, "TotalImpuesto").getAttribute("TotalMontoImpuesto")))
                .isEqualTo(venta.totales().iva()).isEqualTo(CalculadoraIva.redondearTotal(sumaIva));
        assertThat(texto(dte, "GranTotal")).doesNotContain("E");
    }

    @Test
    void sinEmisorEstablecimientoOAfiliacionNoSeGenera() {
        Venta venta = venta(List.of(linea("A", "1.00", 1)));
        Function<DatosDte, String> codigo = d -> {
            try {
                GeneradorDte.generar(d);
                return "sin error";
            } catch (NegocioException e) {
                return e.getCodigo();
            }
        };

        assertThat(codigo.apply(new DatosDte(null, ESTABLECIMIENTO, Receptor.consumidorFinal(), EMITIDA, venta)))
                .isEqualTo("EMISOR_NO_CONFIGURADO");
        assertThat(codigo.apply(new DatosDte(sinAfiliacion(null), ESTABLECIMIENTO, Receptor.consumidorFinal(), EMITIDA,
                venta))).isEqualTo("AFILIACION_IVA_REQUERIDA");
        assertThat(codigo.apply(new DatosDte(sinAfiliacion(AfiliacionIva.PEQ), ESTABLECIMIENTO,
                Receptor.consumidorFinal(), EMITIDA, venta))).isEqualTo("AFILIACION_NO_SOPORTADA");
        assertThat(codigo.apply(new DatosDte(EMISOR, null, Receptor.consumidorFinal(), EMITIDA, venta)))
                .isEqualTo("ESTABLECIMIENTO_NO_CONFIGURADO");
        Establecimiento sinDireccion = new Establecimiento(1, "1", "Matriz", "Zona 1", null, null, null, null);
        assertThat(codigo.apply(new DatosDte(EMISOR, sinDireccion, Receptor.consumidorFinal(), EMITIDA, venta)))
                .isEqualTo("ESTABLECIMIENTO_NO_CONFIGURADO");
        assertThatThrownBy(() -> GeneradorDte.generar(new DatosDte(EMISOR, ESTABLECIMIENTO, Receptor.consumidorFinal(),
                EMITIDA, venta(List.of())))).hasMessageContaining("al menos un producto");
    }

    // Apoyo

    private static Emisor sinAfiliacion(AfiliacionIva afiliacion) {
        return new Emisor(EMISOR.nit(), EMISOR.razonSocial(), EMISOR.nombreComercial(), EMISOR.direccionFiscal(),
                null, null, afiliacion, null);
    }

    private static DatosDte datos(Receptor receptor, LineaVenta... lineas) {
        return new DatosDte(EMISOR, ESTABLECIMIENTO, receptor, EMITIDA, venta(List.of(lineas)));
    }

    /** Una línea con el desglose que calcula el POS al agregarla (HU-04.03). */
    private static LineaVenta linea(String descripcion, String precio, int cantidad) {
        LineaCalculada calculada = CalculadoraIva.linea(new BigDecimal(precio), cantidad);
        return new LineaVenta(1, 1, "7401", descripcion, cantidad, calculada.precioUnitario(),
                calculada.subtotalConIva(), calculada.base(), calculada.iva());
    }

    private static Venta venta(List<LineaVenta> lineas) {
        return new Venta(31, 1042, 7, 1, "Caja 1", 20, EstadoVenta.EN_CURSO, new BigDecimal("0.12"),
                OffsetDateTime.now(), null, lineas);
    }

    private static Document leer(String xml) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            return fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("El DTE no es un XML bien formado: " + e.getMessage(), e);
        }
    }

    private static Element unico(Document dte, String nombre) {
        NodeList nodos = dte.getElementsByTagNameNS(GeneradorDte.ESPACIO_FEL, nombre);
        assertThat(nodos.getLength()).as(nombre).isEqualTo(1);
        return (Element) nodos.item(0);
    }

    private static String texto(Document dte, String nombre) {
        return unico(dte, nombre).getTextContent();
    }

    private static String texto(Element padre, String nombre) {
        return padre.getElementsByTagNameNS(GeneradorDte.ESPACIO_FEL, nombre).item(0).getTextContent();
    }
}
