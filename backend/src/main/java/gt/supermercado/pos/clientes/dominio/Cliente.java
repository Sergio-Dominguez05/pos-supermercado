package gt.supermercado.pos.clientes.dominio;

/**
 * EP-05: un cliente registrado, al que se le factura con su NIT o, si no tiene, con su CUI (HU-05.06). "Consumidor
 * Final" no es un cliente registrado (HU-05.01).
 *
 * @param nit                la identificación normalizada: el NIT sin guiones ni espacios, en mayúsculas (HU-05.03
 *                           crit. 3), o los 13 dígitos del CUI
 * @param direccion          "Ciudad" si no se indicó otra (HU-05.05 crit. 1)
 * @param tipoIdentificacion {@code NIT} o {@code CUI}
 */
public record Cliente(long id, String nit, String nombre, String direccion, String correo,
                      String tipoIdentificacion) {

    public static final String DIRECCION_POR_OMISION = "Ciudad";
    public static final String NIT = "NIT";
    public static final String CUI = "CUI";
}
