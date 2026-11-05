package gt.supermercado.pos.configuracion.dominio;

import java.util.Locale;
import java.util.regex.Pattern;

/** Formato del nombre y de la serie de facturación de una caja (HU-12.05 crit. 2). */
public final class ReglasCaja {

    private static final Pattern SERIE = Pattern.compile("[A-Z0-9]{1,20}");
    private static final int LARGO_MAXIMO_NOMBRE = 40;

    private ReglasCaja() {
    }

    public static String normalizarNombre(String nombre) {
        return nombre.strip();
    }

    public static boolean esNombreValido(String nombre) {
        return !nombre.isEmpty() && nombre.length() <= LARGO_MAXIMO_NOMBRE;
    }

    /** La serie es opcional: vacía significa que la caja no tiene serie propia. Se guarda en mayúsculas. */
    public static String normalizarSerie(String serie) {
        if (serie == null || serie.isBlank()) {
            return null;
        }
        return serie.strip().toUpperCase(Locale.ROOT);
    }

    /** Recibe la serie ya normalizada; {@code null} es válido (sin serie). */
    public static boolean esSerieValida(String serie) {
        return serie == null || SERIE.matcher(serie).matches();
    }
}
