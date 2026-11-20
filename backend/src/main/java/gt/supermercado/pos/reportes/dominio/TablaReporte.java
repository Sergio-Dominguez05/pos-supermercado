package gt.supermercado.pos.reportes.dominio;

import java.util.List;

/**
 * Un reporte listo para exportar (HU-10.13 y HU-10.14): el encabezado con los filtros (HU-10.01 crit. 4), los datos
 * de la empresa, un resumen opcional, las columnas con su tipo, las filas y el pie en el mismo orden y con los mismos
 * totales que la pantalla (HU-10.13 crit. 3), y las notas.
 *
 * <p>Los valores de las celdas son {@link String}, {@link Long}, {@link java.math.BigDecimal},
 * {@link java.time.LocalDate}, {@link java.time.OffsetDateTime} o {@code null}, según el {@link Tipo} de su columna:
 * así el Excel guarda los montos como números y las fechas como fechas (HU-10.14 crit. 2).
 */
public record TablaReporte(CodigoReporte codigo, EncabezadoReporte encabezado, DatosEmpresa empresa,
                           List<Dato> resumen, List<Columna> columnas, List<List<Object>> filas,
                           List<List<Object>> pie, List<String> notas) {

    public enum Tipo { TEXTO, ENTERO, DINERO, FECHA, FECHA_HORA }

    public record Columna(String titulo, Tipo tipo) {}

    /** Un dato suelto del resumen, como el total de faltantes. */
    public record Dato(String etiqueta, Object valor, Tipo tipo) {}

    public TablaReporte conEmpresa(DatosEmpresa datos) {
        return new TablaReporte(codigo, encabezado, datos, resumen, columnas, filas, pie, notas);
    }
}
