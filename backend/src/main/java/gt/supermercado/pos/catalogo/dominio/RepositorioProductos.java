package gt.supermercado.pos.catalogo.dominio;

import java.math.BigDecimal;
import java.util.Optional;

/** Productos del catálogo local. */
public interface RepositorioProductos {

    Optional<Producto> buscarPorId(long id);

    /** Recibe el código ya normalizado. */
    Optional<Producto> buscarPorCodigo(String codigoBarras);

    /** Crea el producto activo (HU-03.01 crit. 6) y devuelve su id. */
    long crear(String codigoBarras, String descripcion, BigDecimal precio);
}
