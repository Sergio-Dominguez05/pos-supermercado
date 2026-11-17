package gt.supermercado.pos.configuracion.dominio;

/**
 * HU-08.02: la afiliación del emisor al IVA, con el código que usa el DTE. De ella dependen las frases del documento.
 * El POS desglosa el IVA del 12 % en cada factura, que es el régimen General; el Pequeño Contribuyente emite otro
 * tipo de documento (FPEQ), fuera del alcance de V1.
 */
public enum AfiliacionIva {
    /** Régimen General del IVA. */
    GEN,
    /** Pequeño Contribuyente. */
    PEQ
}
