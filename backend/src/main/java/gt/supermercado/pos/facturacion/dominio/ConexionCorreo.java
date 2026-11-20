package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.15: los datos para conectarse al servidor de correo, con la contraseña descifrada. Solo existe en memoria
 * mientras se envía; {@link #toString()} no la muestra.
 *
 * @param usuario {@code null} si el servidor no pide autenticación
 */
public record ConexionCorreo(String servidor, int puerto, SeguridadCorreo seguridad, String usuario, String secreto,
                             String remitente, String nombreRemitente, int timeoutMs) {

    @Override
    public String toString() {
        return "ConexionCorreo[servidor=%s, puerto=%d, seguridad=%s, usuario=%s, remitente=%s]".formatted(servidor,
                puerto, seguridad, usuario, remitente);
    }
}
