package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.02 crit. 1: a quién se emite la factura. Con NIT, con CUI (HU-05.06) o a Consumidor Final (HU-05.01). Mientras
 * no exista EP-05 toda venta es a Consumidor Final; el generador del DTE ya acepta los tres tipos.
 *
 * @param identificacion el NIT normalizado, el CUI o "CF"
 */
public record Receptor(TipoReceptor tipo, String identificacion, String nombre) {

    public enum TipoReceptor { NIT, CUI, CF }

    public static final String NOMBRE_CONSUMIDOR_FINAL = "Consumidor Final";

    public static Receptor consumidorFinal() {
        return new Receptor(TipoReceptor.CF, "CF", NOMBRE_CONSUMIDOR_FINAL);
    }
}
