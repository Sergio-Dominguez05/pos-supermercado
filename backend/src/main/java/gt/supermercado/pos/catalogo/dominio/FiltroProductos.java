package gt.supermercado.pos.catalogo.dominio;

/**
 * Consulta de la lista del catálogo (HU-03.04).
 *
 * @param texto       busca en el código y en la descripción, sin distinguir mayúsculas ni tildes; {@code null} = todos
 *                    (crit. 2)
 * @param activo      {@code true} o {@code false} para filtrar por estado; {@code null} = todos (crit. 3)
 * @param orden       campo por el que se ordena (crit. 4)
 * @param descendente si el orden va de mayor a menor
 * @param pagina      número de página, desde 0 (crit. 5)
 * @param tamano      productos por página, de 1 a {@link #TAMANO_MAXIMO}
 */
public record FiltroProductos(String texto, Boolean activo, OrdenProductos orden, boolean descendente, int pagina,
                              int tamano) {

    public static final int TAMANO_POR_DEFECTO = 25;
    public static final int TAMANO_MAXIMO = 100;

    public FiltroProductos {
        texto = texto == null || texto.isBlank() ? null : texto.strip();
        orden = orden == null ? OrdenProductos.DESCRIPCION : orden;
        pagina = Math.max(pagina, 0);
        tamano = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
    }

    /** Campos por los que se puede ordenar la lista (crit. 4). */
    public enum OrdenProductos {
        DESCRIPCION,
        PRECIO,
        MODIFICADO
    }
}
