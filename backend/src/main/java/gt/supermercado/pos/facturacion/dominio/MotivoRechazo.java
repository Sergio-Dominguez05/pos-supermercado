package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.06: por qué el certificador rechazó la factura, en lenguaje comprensible (crit. 1), y si el cajero lo puede
 * corregir en caja (crit. 2) o queda pendiente para un supervisor (crit. 3).
 *
 * @param mensaje        lo que se le muestra al cajero
 * @param codigo         el código del certificador, tal como llegó
 * @param detalle        el mensaje original del certificador
 */
public record MotivoRechazo(Tipo tipo, String mensaje, String codigo, String detalle) {

    public enum Tipo {
        /** El NIT, el CUI o el nombre del cliente: el cajero lo corrige y reenvía (crit. 2). */
        RECEPTOR,
        /** Los montos no cuadran con los del certificador (HU-04.09 crit. 2). */
        MONTOS,
        /** Los datos del emisor o del establecimiento: se corrigen en la configuración. */
        EMISOR,
        /** Cualquier otro error del certificador. */
        OTRO
    }

    /** Crit. 2 y 3: solo un error del receptor se corrige en caja; los demás quedan para un supervisor. */
    public boolean corregibleEnCaja() {
        return tipo == Tipo.RECEPTOR;
    }
}
