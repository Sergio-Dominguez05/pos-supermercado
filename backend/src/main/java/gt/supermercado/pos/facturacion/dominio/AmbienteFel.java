package gt.supermercado.pos.facturacion.dominio;

/** HU-12.03 crit. 1: el ambiente del certificador. En PRUEBAS no se emiten facturas reales (HU-08.01 crit. 3). */
public enum AmbienteFel {
    PRUEBAS,
    PRODUCCION
}
