package gt.supermercado.pos.catalogo.dominio;

import java.util.List;

/**
 * El archivo del catálogo en Excel o CSV: la plantilla de carga (HU-03.05 crit. 1) y la exportación (HU-03.12), que
 * comparten columnas para que un catálogo exportado se pueda volver a cargar tal cual (HU-03.12 crit. 2).
 * <ul>
 *   <li>Las tres primeras columnas son las de la plantilla: código, descripción y precio (con IVA).</li>
 *   <li>La exportación agrega el estado y la fecha de la última modificación; la carga las ignora.</li>
 * </ul>
 */
public final class ArchivoCatalogo {

    public static final String CODIGO = "Código";
    public static final String DESCRIPCION = "Descripción";
    public static final String PRECIO = "Precio";
    public static final String ESTADO = "Estado";
    public static final String MODIFICADO = "Última modificación";

    /** HU-03.05 crit. 1: las columnas de la plantilla de carga. */
    public static final List<String> COLUMNAS_PLANTILLA = List.of(CODIGO, DESCRIPCION, PRECIO);

    /** HU-03.12 crit. 1: las de la plantilla, más el estado y la última modificación. */
    public static final List<String> COLUMNAS_EXPORTACION = List.of(CODIGO, DESCRIPCION, PRECIO, ESTADO, MODIFICADO);

    private ArchivoCatalogo() {
    }

    /** Los formatos del archivo. */
    public enum Formato {

        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv; charset=UTF-8");

        private final String extension;
        private final String tipoContenido;

        Formato(String extension, String tipoContenido) {
            this.extension = extension;
            this.tipoContenido = tipoContenido;
        }

        public String extension() {
            return extension;
        }

        public String tipoContenido() {
            return tipoContenido;
        }
    }

    /** Escribe el catálogo (o la plantilla vacía) en el formato pedido. */
    public interface Escritor {

        byte[] exportar(List<Producto> productos, Formato formato);

        /** HU-03.05 crit. 1: la plantilla de carga, solo con los títulos de sus tres columnas. */
        byte[] plantilla(Formato formato);
    }

    /**
     * HU-03.05: una fila del archivo tal como viene, antes de validarla. {@code numero} es el renglón en la hoja
     * (el de los títulos es el 1), para decirle al usuario dónde está cada error.
     */
    public record Fila(int numero, String codigo, String descripcion, String precio) {}

    /** HU-03.05: lee las filas de un archivo de Excel (.xlsx) o CSV con las columnas de la plantilla. */
    public interface Lector {

        /**
         * Las filas con datos, sin las vacías. Lanza {@code NegocioException} si el archivo no se puede leer o le falta
         * alguna de las columnas de la plantilla; las columnas extra (estado, última modificación) se ignoran.
         */
        List<Fila> leer(byte[] contenido);
    }
}
