package gt.supermercado.pos.facturacion.dominio;

import java.util.Optional;

/** La única fila de {@code config_fel} (HU-12.03). */
public interface RepositorioConexionFel {

    Optional<ConfiguracionFel> configuracion();

    /** Crea o reemplaza la configuración; el secreto llega ya cifrado. */
    void guardar(String certificador, String urlServicio, String usuario, byte[] secretoCifrado, AmbienteFel ambiente,
                 int timeoutMs, long usuarioId);
}
