package gt.supermercado.pos.catalogo.dominio;

import java.math.BigDecimal;
import java.util.Optional;

/** Productos del catálogo local. */
public interface RepositorioProductos {

    Optional<Producto> buscarPorId(long id);

    /** Recibe el código ya normalizado. */
    Optional<Producto> buscarPorCodigo(String codigoBarras);

    /** Una página de la lista, con búsqueda, filtro por estado y orden (HU-03.04). */
    PaginaProductos buscar(FiltroProductos filtro);

    /** Crea el producto activo (HU-03.01 crit. 6) y devuelve su id. */
    long crear(String codigoBarras, String descripcion, BigDecimal precio);

    /** Cambia la descripción y el precio, y actualiza la fecha de modificación. El código no se toca (HU-03.02). */
    void actualizar(long id, String descripcion, BigDecimal precio);

    /** Activa o desactiva el producto y actualiza la fecha de modificación (HU-03.03). Nunca se borra. */
    void cambiarActivo(long id, boolean activo);
}
