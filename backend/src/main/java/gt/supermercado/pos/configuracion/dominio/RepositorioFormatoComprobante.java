package gt.supermercado.pos.configuracion.dominio;

import java.util.Optional;

/** HU-12.09: el formato del comprobante, uno para todo el supermercado. */
public interface RepositorioFormatoComprobante {

    /** Vacío si nunca se personalizó. */
    Optional<FormatoComprobante> obtener();

    void guardar(String encabezado, String pie, LogoTicket logo, long usuarioId);
}
