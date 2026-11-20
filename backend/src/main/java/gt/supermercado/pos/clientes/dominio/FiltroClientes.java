package gt.supermercado.pos.clientes.dominio;

/** Búsqueda y página de la lista de clientes (HU-05.10). */
public record FiltroClientes(String texto, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 25;
    public static final int TAMANO_MAXIMO = 100;

    public FiltroClientes {
        texto = texto == null || texto.isBlank() ? null : texto.strip();
        pagina = Math.max(pagina, 0);
        tamano = tamano <= 0 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
    }
}
