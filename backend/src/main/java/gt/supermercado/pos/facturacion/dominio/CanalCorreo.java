package gt.supermercado.pos.facturacion.dominio;

/**
 * HU-08.15: el medio por el que salen los correos. En producción es SMTP; en desarrollo y en las pruebas, un canal
 * simulado que no envía nada ({@code pos.correo.modo}).
 */
public interface CanalCorreo {

    /** Envía el mensaje. Lanza {@link CorreoNoEnviadoException} con el motivo si el servidor no lo acepta. */
    void enviar(ConexionCorreo conexion, MensajeCorreo mensaje);
}
