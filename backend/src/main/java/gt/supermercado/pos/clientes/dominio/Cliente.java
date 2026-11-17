package gt.supermercado.pos.clientes.dominio;

/**
 * EP-05: un cliente registrado, al que se le factura con su NIT. "Consumidor Final" no es un cliente registrado
 * (HU-05.01).
 *
 * @param nit       normalizado: sin guiones ni espacios, en mayúsculas (HU-05.03 crit. 3)
 * @param direccion "Ciudad" si no se indicó otra (HU-05.05 crit. 1)
 */
public record Cliente(long id, String nit, String nombre, String direccion, String correo) {

    public static final String DIRECCION_POR_OMISION = "Ciudad";
}
