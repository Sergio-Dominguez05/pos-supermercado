package gt.supermercado.pos.catalogo.dominio;

import java.util.List;

/**
 * Una página de la lista del catálogo (HU-03.04 crit. 5).
 *
 * @param total cantidad de productos que cumplen el filtro, en todas las páginas
 */
public record PaginaProductos(List<Producto> productos, int pagina, int tamano, long total) {

    public PaginaProductos {
        productos = List.copyOf(productos);
    }

    public int totalPaginas() {
        return (int) ((total + tamano - 1) / tamano);
    }
}
