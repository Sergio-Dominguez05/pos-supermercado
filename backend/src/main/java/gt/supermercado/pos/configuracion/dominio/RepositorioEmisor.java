package gt.supermercado.pos.configuracion.dominio;

import java.util.Optional;

/** La única fila de {@code emisor} (HU-12.01). */
public interface RepositorioEmisor {

    Optional<Emisor> emisor();

    /** Crea o reemplaza los datos del emisor. */
    void guardar(Emisor emisor, long usuarioId);
}
