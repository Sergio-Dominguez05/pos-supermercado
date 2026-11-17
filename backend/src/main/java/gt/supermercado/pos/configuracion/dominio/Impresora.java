package gt.supermercado.pos.configuracion.dominio;

import java.time.OffsetDateTime;

/**
 * HU-12.08: una impresora térmica de la red. El servidor le envía los tickets ESC/POS a {@code host:puerto}.
 *
 * @param anchoMm       58 u 80 mm de papel (crit. 1)
 * @param copiasFactura cuántas copias de cada factura imprime (crit. 3)
 * @param copiasCierre  cuántas copias del cierre de caja imprime (crit. 3, HU-09.08)
 */
public record Impresora(long id, String nombre, String host, int puerto, int anchoMm, int copiasFactura,
                        int copiasCierre, OffsetDateTime modificadoEn) {

    public static final int PUERTO_POR_OMISION = 9100;
}
