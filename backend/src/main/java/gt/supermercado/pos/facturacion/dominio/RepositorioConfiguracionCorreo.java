package gt.supermercado.pos.facturacion.dominio;

import java.util.Optional;

/** HU-08.15: el servidor de correo con que el POS envía las facturas (una sola configuración). */
public interface RepositorioConfiguracionCorreo {

    Optional<ConfiguracionCorreo> configuracion();

    /** Crea o reemplaza la configuración. {@code usuario} y {@code secretoCifrado} van juntos o ambos vacíos. */
    void guardar(String servidor, int puerto, SeguridadCorreo seguridad, String usuario, byte[] secretoCifrado,
                 String remitente, String nombreRemitente, int timeoutMs, long usuarioId);
}
