package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parámetros de caja y venta que define el Administrador (HU-12.13, #119). Mientras esa historia no exista, cada
 * parámetro conserva el valor inicial de la migración {@code V16__parametros.sql}; si faltara en la base, se usa el
 * mismo valor por defecto.
 */
@Service
public class Parametros {

    static final String CANTIDAD_MAX_LINEA = "cantidad_max_linea";
    static final int CANTIDAD_MAX_LINEA_POR_DEFECTO = 50;

    private final RepositorioParametros parametros;

    public Parametros(RepositorioParametros parametros) {
        this.parametros = parametros;
    }

    /** HU-06.04 crit. 4: la cantidad por línea a partir de la cual se pide confirmación al cajero. */
    @Transactional(readOnly = true)
    public int cantidadMaximaPorLinea() {
        return parametros.entero(CANTIDAD_MAX_LINEA).filter(valor -> valor > 0).orElse(CANTIDAD_MAX_LINEA_POR_DEFECTO);
    }
}
