package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-12.09 Personalizar el formato del comprobante: el logo y los mensajes del supermercado.
 * <ul>
 *   <li>Crit. 1: un logo en blanco y negro ({@code null} si no hay).</li>
 *   <li>Crit. 2: un mensaje en el encabezado (debajo de los datos del emisor) y otro en el pie; vacíos no se
 *       imprimen. Sin configurar, el pie es "¡Gracias por su compra!", como hasta ahora.</li>
 *   <li>Crit. 4: solo son agregados. Los datos que exige la SAT y el bloque Subtotal, IVA y TOTAL los arma siempre el
 *       ticket y no dependen de esta configuración.</li>
 * </ul>
 */
public record FormatoComprobante(String encabezado, String pie, LogoTicket logo, OffsetDateTime modificadoEn) {

    public static final int LARGO_MAXIMO_ENCABEZADO = 120;
    public static final int LARGO_MAXIMO_PIE = 200;
    public static final String PIE_POR_OMISION = "¡Gracias por su compra!";

    /** El formato de siempre: sin logo, sin encabezado y con el agradecimiento en el pie. */
    public static final FormatoComprobante POR_OMISION = new FormatoComprobante(null, PIE_POR_OMISION, null, null);
}
