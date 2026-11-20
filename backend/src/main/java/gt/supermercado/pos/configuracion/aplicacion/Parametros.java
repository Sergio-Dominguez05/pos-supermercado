package gt.supermercado.pos.configuracion.aplicacion;

import gt.supermercado.pos.configuracion.dominio.RepositorioParametros;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    static final String LIMITE_CONSUMIDOR_FINAL = "limite_consumidor_final";
    /** HU-12.07 crit. 1: el límite vigente de la SAT para facturar a Consumidor Final. */
    public static final BigDecimal LIMITE_CONSUMIDOR_FINAL_POR_DEFECTO = new BigDecimal("2500.00");

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

    /**
     * HU-05.07 y HU-12.07: desde este total (inclusive), la venta no se factura a Consumidor Final. Se lee en cada
     * validación, así que un cambio se aplica de inmediato (HU-12.07 crit. 2).
     */
    @Transactional(readOnly = true)
    public BigDecimal limiteConsumidorFinal() {
        return parametros.decimal(LIMITE_CONSUMIDOR_FINAL).filter(valor -> valor.signum() > 0)
                .map(valor -> valor.setScale(2, java.math.RoundingMode.HALF_UP))
                .orElse(LIMITE_CONSUMIDOR_FINAL_POR_DEFECTO);
    }

    /** HU-09.11: indica si el cajero debe ingresar el cierre de lote sin conocer antes el total registrado. */
    @Transactional(readOnly = true)
    public boolean cierreCiego() {
        return parametros.booleano(CIERRE_CIEGO).orElse(false);
    }
}
