package gt.supermercado.pos.caja.dominio;

import java.util.List;

/** HU-09.13 crit. 1: una página de cierres, del más reciente al más antiguo, con el total que cumple los filtros. */
public record PaginaCierres(List<CierreDeTurno> cierres, int pagina, int tamanoPagina, long total) {

    public PaginaCierres {
        cierres = List.copyOf(cierres);
    }
}
