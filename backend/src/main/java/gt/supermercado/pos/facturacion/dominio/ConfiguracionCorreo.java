package gt.supermercado.pos.facturacion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-08.15: el servidor de correo guardado, tal como se muestra: sin la contraseña. {@code secretoCifrado} es lo que
 * está en la base de datos ({@code null} si el servidor no pide usuario); solo la capa de aplicación lo descifra.
 */
public record ConfiguracionCorreo(String servidor, int puerto, SeguridadCorreo seguridad, String usuario,
                                  String remitente, String nombreRemitente, int timeoutMs, OffsetDateTime modificadoEn,
                                  byte[] secretoCifrado) {
}
