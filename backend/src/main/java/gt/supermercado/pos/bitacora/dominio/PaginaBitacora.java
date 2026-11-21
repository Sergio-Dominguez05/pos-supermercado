package gt.supermercado.pos.bitacora.dominio;

import java.util.List;

/** HU-11.06 crit. 2: una página de registros, del más reciente al más antiguo, con el total que cumple los filtros. */
public record PaginaBitacora(List<RegistroConsultado> registros, int pagina, int tamanoPagina, long total) {

    public PaginaBitacora {
        registros = List.copyOf(registros);
    }
}
