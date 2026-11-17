package gt.supermercado.pos.facturacion.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.configuracion.dominio.AfiliacionIva;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.impuestos.dominio.Totales;
import gt.supermercado.pos.ventas.dominio.LineaVenta;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * HU-08.02 Generar el documento de la factura (Architecture.md §7.2, {@code GeneradorDte}).
 * <ul>
 *   <li>Crit. 1: emisor (NIT, nombre y establecimiento), receptor (NIT, CUI o CF, y nombre), fecha de emisión, moneda
 *       GTQ, líneas con cantidad, descripción, precio, base imponible e IVA con hasta 6 decimales, y totales con 2.</li>
 *   <li>Crit. 2: esquema FEL de la SAT {@code GTDocumento} versión 0.1, espacio de nombres
 *       {@value #ESPACIO_FEL}. El certificador elegido lo valida y firma (HU-08.03).</li>
 *   <li>Crit. 3 y HU-04.09 crit. 1: los montos son exactamente los que guardó el POS; aquí no se recalcula nada.</li>
 *   <li>Crit. 4: el tipo de documento es Factura ({@code FACT}).</li>
 * </ul>
 * Las frases dependen de la afiliación al IVA del emisor: en el régimen General, la frase 1 escenario 1 (sujeto a
 * pagos trimestrales del ISR). El Pequeño Contribuyente emite FPEQ, sin IVA desglosado, fuera del alcance de V1.
 */
public final class GeneradorDte {

    public static final String ESPACIO_FEL = "http://www.sat.gob.gt/dte/fel/0.2.0";
    public static final String VERSION_ESQUEMA = "0.1";
    public static final String TIPO_FACTURA = "FACT";
    public static final String MONEDA = "GTQ";

    private static final String PREFIJO = "dte:";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private GeneradorDte() {
    }

    /** El XML del DTE, listo para enviarlo al certificador. Lanza {@link NegocioException} si falta un dato. */
    public static String generar(DatosDte datos) {
        validar(datos);
        Document documento = nuevoDocumento();
        Element raiz = elemento(documento, "GTDocumento");
        raiz.setAttribute("Version", VERSION_ESQUEMA);
        raiz.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:dte", ESPACIO_FEL);
        documento.appendChild(raiz);

        Element sat = hijo(raiz, "SAT");
        sat.setAttribute("ClaseDocumento", "dte");
        Element dte = hijo(sat, "DTE");
        dte.setAttribute("ID", "DatosCertificados");
        Element emision = hijo(dte, "DatosEmision");
        emision.setAttribute("ID", "DatosEmision");

        Element generales = hijo(emision, "DatosGenerales");
        generales.setAttribute("Tipo", TIPO_FACTURA);
        generales.setAttribute("FechaHoraEmision", FECHA.format(datos.emitidaEn().truncatedTo(ChronoUnit.SECONDS)));
        generales.setAttribute("CodigoMoneda", MONEDA);

        emisor(emision, datos.emisor(), datos.establecimiento());
        receptor(emision, datos.receptor());
        Element frase = hijo(hijo(emision, "Frases"), "Frase");
        frase.setAttribute("TipoFrase", "1");
        frase.setAttribute("CodigoEscenario", "1");

        Element items = hijo(emision, "Items");
        int numero = 1;
        for (LineaVenta linea : datos.venta().lineas()) {
            item(items, linea, numero++);
        }
        totales(emision, datos.venta().totales());
        return texto(documento);
    }

    private static void validar(DatosDte datos) {
        if (datos.emisor() == null) {
            throw new NegocioException("EMISOR_NO_CONFIGURADO",
                    "Faltan los datos del emisor: configúrelos en Configuración › Emisor.");
        }
        if (datos.emisor().afiliacionIva() == null) {
            throw new NegocioException("AFILIACION_IVA_REQUERIDA",
                    "Falta la afiliación al IVA del emisor: complétela en Configuración › Emisor.");
        }
        if (datos.emisor().afiliacionIva() != AfiliacionIva.GEN) {
            throw new NegocioException("AFILIACION_NO_SOPORTADA",
                    "El Pequeño Contribuyente emite otro tipo de documento (FPEQ), que esta versión no genera.");
        }
        Establecimiento establecimiento = datos.establecimiento();
        if (establecimiento == null || establecimiento.codigoPostal() == null || establecimiento.municipio() == null
                || establecimiento.departamento() == null) {
            throw new NegocioException("ESTABLECIMIENTO_NO_CONFIGURADO",
                    "Faltan los datos del establecimiento SAT: complételos en Configuración › Establecimiento.");
        }
        if (datos.venta().lineas().isEmpty()) {
            throw new NegocioException("VENTA_VACIA", "Una factura necesita al menos un producto.");
        }
    }

    private static void emisor(Element emision, Emisor emisor, Establecimiento establecimiento) {
        Element elemento = hijo(emision, "Emisor");
        elemento.setAttribute("NITEmisor", emisor.nit());
        elemento.setAttribute("NombreEmisor", emisor.razonSocial());
        elemento.setAttribute("CodigoEstablecimiento", establecimiento.codigoSat());
        elemento.setAttribute("NombreComercial", establecimiento.nombre());
        elemento.setAttribute("AfiliacionIVA", emisor.afiliacionIva().name());
        if (emisor.correo() != null) {
            elemento.setAttribute("CorreoEmisor", emisor.correo());
        }
        Element direccion = hijo(elemento, "DireccionEmisor");
        texto(direccion, "Direccion", establecimiento.direccion());
        texto(direccion, "CodigoPostal", establecimiento.codigoPostal());
        texto(direccion, "Municipio", establecimiento.municipio());
        texto(direccion, "Departamento", establecimiento.departamento());
        texto(direccion, "Pais", "GT");
    }

    private static void receptor(Element emision, Receptor receptor) {
        Element elemento = hijo(emision, "Receptor");
        elemento.setAttribute("IDReceptor", receptor.identificacion());
        elemento.setAttribute("NombreReceptor", receptor.nombre());
        if (receptor.tipo() == Receptor.TipoReceptor.CUI) {
            elemento.setAttribute("TipoEspecial", "CUI");
        }
    }

    /** Cada línea con los montos guardados: precio = subtotal con IVA, gravable = base imponible, impuesto = IVA. */
    private static void item(Element items, LineaVenta linea, int numero) {
        Element item = hijo(items, "Item");
        item.setAttribute("NumeroLinea", String.valueOf(numero));
        item.setAttribute("BienOServicio", "B");
        texto(item, "Cantidad", String.valueOf(linea.cantidad()));
        texto(item, "UnidadMedida", "UNI");
        texto(item, "Descripcion", linea.descripcion());
        texto(item, "PrecioUnitario", monto(linea.precioUnitario()));
        texto(item, "Precio", monto(linea.subtotalConIva()));
        texto(item, "Descuento", "0");
        Element impuesto = hijo(hijo(item, "Impuestos"), "Impuesto");
        texto(impuesto, "NombreCorto", "IVA");
        texto(impuesto, "CodigoUnidadGravable", "1");
        texto(impuesto, "MontoGravable", monto(linea.baseImponible()));
        texto(impuesto, "MontoImpuesto", monto(linea.iva()));
        texto(item, "Total", monto(linea.subtotalConIva()));
    }

    private static void totales(Element emision, Totales totales) {
        Element elemento = hijo(emision, "Totales");
        Element impuesto = hijo(hijo(elemento, "TotalImpuestos"), "TotalImpuesto");
        impuesto.setAttribute("NombreCorto", "IVA");
        impuesto.setAttribute("TotalMontoImpuesto", monto(totales.iva()));
        texto(elemento, "GranTotal", monto(totales.total()));
    }

    /** El monto tal como se guardó, sin notación científica ni redondeos (crit. 3). */
    static String monto(BigDecimal valor) {
        return valor.toPlainString();
    }

    private static Document nuevoDocumento() {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            return fabrica.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo crear el documento XML.", e);
        }
    }

    private static Element elemento(Document documento, String nombre) {
        return documento.createElementNS(ESPACIO_FEL, PREFIJO + nombre);
    }

    private static Element hijo(Element padre, String nombre) {
        Element hijo = elemento(padre.getOwnerDocument(), nombre);
        padre.appendChild(hijo);
        return hijo;
    }

    private static void texto(Element padre, String nombre, String valor) {
        hijo(padre, nombre).setTextContent(valor);
    }

    private static String texto(Document documento) {
        try {
            TransformerFactory fabrica = TransformerFactory.newInstance();
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformador = fabrica.newTransformer();
            transformador.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformador.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter salida = new StringWriter();
            transformador.transform(new DOMSource(documento), new StreamResult(salida));
            return salida.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo escribir el XML del DTE.", e);
        }
    }
}
