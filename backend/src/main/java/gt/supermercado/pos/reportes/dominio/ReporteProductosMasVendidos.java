package gt.supermercado.pos.reportes.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * HU-10.05 Reporte de productos más vendidos.
 * <ul>
 *   <li>Crit. 1: por producto, el código, la descripción, la cantidad vendida y el monto vendido (IVA incluido).</li>
 *   <li>Crit. 2: ordenado por cantidad o por monto ({@link OrdenProductos}).</li>
 *   <li>Crit. 3: limitado a los primeros {@code limite} productos (por omisión, {@value #LIMITE_POR_OMISION}).</li>
 *   <li>Crit. 4: solo las líneas vigentes de ventas finalizadas en el período; las de facturas anuladas no se cuentan
 *       (las excluye {@link RepositorioReportes}).</li>
 * </ul>
 * La fila de totales suma los productos mostrados; {@code productosVendidos} dice cuántos productos distintos se
 * vendieron en el período, para saber si la lista está recortada.
 */
public record ReporteProductosMasVendidos(EncabezadoReporte encabezado, OrdenProductos ordenarPor, int limite,
                                          long productosVendidos, List<Fila> filas, Fila totales) {

    public static final String TITULO = "Productos más vendidos";
    public static final int LIMITE_POR_OMISION = 20;
    /** Un tope para que el reporte siga siendo un "top" y no el catálogo entero. */
    public static final int LIMITE_MAXIMO = 500;

    /** Un producto en su posición del ranking, o la fila de totales ({@code posicion} y {@code productoId} nulos). */
    public record Fila(Integer posicion, Long productoId, String codigo, String descripcion, long cantidad,
                       BigDecimal monto) {}

    /** Lo que trae la consulta: los productos ya ordenados y recortados, y cuántos se vendieron en total. */
    public record Resultado(long productosVendidos, List<Fila> filas) {}

    public static ReporteProductosMasVendidos de(EncabezadoReporte encabezado, OrdenProductos orden, int limite,
                                                 Resultado resultado) {
        List<Fila> filas = resultado.filas();
        Fila totales = new Fila(null, null, null, "Total", filas.stream().mapToLong(Fila::cantidad).sum(),
                filas.stream().map(Fila::monto).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP));
        return new ReporteProductosMasVendidos(encabezado, orden, limite, resultado.productosVendidos(),
                List.copyOf(filas), totales);
    }

    /** Crit. 3: el límite pedido, o el de omisión; entre 1 y {@value #LIMITE_MAXIMO}. */
    public static int limiteValido(Integer pedido) {
        if (pedido == null) {
            return LIMITE_POR_OMISION;
        }
        if (pedido < 1 || pedido > LIMITE_MAXIMO) {
            throw new NegocioException("FILTRO_LIMITE_INVALIDO",
                    "Indique cuántos productos mostrar, entre 1 y %d.".formatted(LIMITE_MAXIMO));
        }
        return pedido;
    }
}
