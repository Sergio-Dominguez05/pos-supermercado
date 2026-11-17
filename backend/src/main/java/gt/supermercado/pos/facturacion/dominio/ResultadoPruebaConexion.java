package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.01 crit. 3 y HU-12.03 crit. 2: resultado de probar la conexión con el certificador, sin emitir facturas. El
 * mensaje está listo para mostrarse al Administrador.
 */
public record ResultadoPruebaConexion(boolean exitosa, String mensaje) {

    public static ResultadoPruebaConexion exitosa(String mensaje) {
        return new ResultadoPruebaConexion(true, mensaje);
    }

    public static ResultadoPruebaConexion fallida(String mensaje) {
        return new ResultadoPruebaConexion(false, mensaje);
    }
}
