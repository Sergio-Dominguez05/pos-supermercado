package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parámetros de caja y venta que define el Administrador (HU-12.13, #119). Si un parámetro falta o tiene un tipo
 * incorrecto, se usa su valor inicial seguro.
 */
@Service
public class Parametros {

    static final String CANTIDAD_MAX_LINEA = "cantidad_max_linea";
    static final int CANTIDAD_MAX_LINEA_POR_DEFECTO = 50;
    public static final int CANTIDAD_MAXIMA_ABSOLUTA = 9999;
    static final String CIERRE_CIEGO = "cierre_ciego";

    private final RepositorioParametros parametros;

    public Parametros(RepositorioParametros parametros) {
        this.parametros = parametros;
    }

    /** HU-06.04 crit. 4: la cantidad por línea a partir de la cual se pide confirmación al cajero. */
    @Transactional(readOnly = true)
    public int cantidadMaximaPorLinea() {
        return parametros.entero(CANTIDAD_MAX_LINEA)
                .filter(valor -> valor > 0 && valor <= CANTIDAD_MAXIMA_ABSOLUTA)
                .orElse(CANTIDAD_MAX_LINEA_POR_DEFECTO);
    }

    /** HU-09.11: indica si el cajero debe ingresar el cierre de lote sin conocer antes el total registrado. */
    @Transactional(readOnly = true)
    public boolean cierreCiego() {
        return parametros.booleano(CIERRE_CIEGO).orElse(false);
    }
}
