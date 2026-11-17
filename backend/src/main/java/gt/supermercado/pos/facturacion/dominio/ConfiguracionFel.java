package gt.supermercado.pos.facturacion.dominio;

import java.time.OffsetDateTime;

/**
 * La conexión FEL guardada, tal como se muestra: sin la llave o token (HU-12.15 crit. 2). {@code secretoCifrado} es
 * lo que está en la base de datos; solo la capa de aplicación lo descifra para conectarse. {@code nitCertificador}
 * va en la factura impresa (HU-08.05 crit. 1); puede faltar en configuraciones guardadas antes.
 */
public record ConfiguracionFel(String certificador, String urlServicio, String usuario, AmbienteFel ambiente,
                               int timeoutMs, OffsetDateTime modificadoEn, byte[] secretoCifrado,
                               String nitCertificador) {
}
