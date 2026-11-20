package gt.supermercado.pos.facturacion.dominio;

/** HU-08.15: cómo se protege la conexión con el servidor de correo (SMTP). */
public enum SeguridadCorreo {
    /** Conexión normal que pasa a cifrada (puerto 587, lo usual). */
    STARTTLS,
    /** Conexión cifrada desde el inicio (puerto 465). */
    SSL,
    /** Sin cifrar: solo para un servidor dentro de la red del supermercado. */
    NINGUNA
}
