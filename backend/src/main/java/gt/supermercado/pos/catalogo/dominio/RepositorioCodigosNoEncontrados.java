package gt.supermercado.pos.catalogo.dominio;

import java.util.List;

/** Códigos buscados que no existen en el catálogo (HU-03.08 crit. 4). */
public interface RepositorioCodigosNoEncontrados {

    /** Registra una búsqueda fallida; {@code cajaId} es {@code null} si el equipo no está vinculado a una caja. */
    void registrar(String codigo, Long cajaId, long usuarioId);

    /** Los códigos pendientes, agrupados, con el más reciente primero. */
    List<CodigoNoEncontrado> pendientes();

    /** Marca como resueltas todas las búsquedas de ese código: ya existe un producto con él. */
    void resolver(String codigo);
}
