package gt.supermercado.pos.facturacion.dominio;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * HU-08.06 crit. 1 (Architecture.md §7.2, {@code TraductorErrores}): convierte el error del certificador en un mensaje
 * que el cajero entiende y decide si se corrige en caja.
 *
 * <p>Cada certificador tiene sus propios códigos y el que se usará todavía no se elige, así que la clasificación se
 * hace por las palabras del mensaje, que todos los certificadores expresan con los términos de la SAT (NIT, receptor,
 * montos, emisor...). Al contratar el certificador se pueden agregar sus códigos a {@link #CODIGOS}.
 */
public final class TraductorErrores {

    /** Códigos conocidos: hoy, los del certificador simulado. */
    private static final List<Codigo> CODIGOS = List.of(
            new Codigo("SIM-001", MotivoRechazo.Tipo.RECEPTOR),
            new Codigo("SIM-002", MotivoRechazo.Tipo.MONTOS));

    private static final List<String> DEL_RECEPTOR = List.of("receptor", "nit", "cui", "nombre del cliente",
            "contribuyente");
    private static final List<String> DE_MONTOS = List.of("monto", "total", "iva", "impuesto", "gravable", "precio",
            "descuento", "cuadra");
    private static final List<String> DEL_EMISOR = List.of("emisor", "establecimiento", "afiliacion", "frase");

    private record Codigo(String codigo, MotivoRechazo.Tipo tipo) {}

    private TraductorErrores() {
    }

    public static MotivoRechazo traducir(String codigo, String mensajeDelCertificador) {
        String detalle = mensajeDelCertificador == null ? "" : mensajeDelCertificador.strip();
        MotivoRechazo.Tipo tipo = CODIGOS.stream().filter(c -> c.codigo().equalsIgnoreCase(codigo)).findFirst()
                .map(Codigo::tipo).orElseGet(() -> porPalabras(detalle));
        return new MotivoRechazo(tipo, mensaje(tipo, detalle), codigo, detalle);
    }

    /**
     * El emisor va primero ("NIT del emisor" o "afiliación al IVA" son del emisor, no del cliente ni de los montos) y los
     * montos antes que el receptor.
     */
    private static MotivoRechazo.Tipo porPalabras(String detalle) {
        String texto = sinTildes(detalle);
        if (contiene(texto, DEL_EMISOR)) {
            return MotivoRechazo.Tipo.EMISOR;
        }
        if (contiene(texto, DE_MONTOS)) {
            return MotivoRechazo.Tipo.MONTOS;
        }
        if (contiene(texto, DEL_RECEPTOR)) {
            return MotivoRechazo.Tipo.RECEPTOR;
        }
        return MotivoRechazo.Tipo.OTRO;
    }

    private static String mensaje(MotivoRechazo.Tipo tipo, String detalle) {
        return switch (tipo) {
            case RECEPTOR -> "El NIT o el nombre del cliente no es válido para la SAT. Corrija el cliente y reenvíe "
                    + "la factura; no hace falta volver a cobrar.";
            case MONTOS -> "Los montos de la factura no coinciden con los que calculó el certificador. La venta queda "
                    + "pendiente de facturar: avise a su supervisor.";
            case EMISOR -> "Los datos del emisor o del establecimiento no son válidos para la SAT. La venta queda "
                    + "pendiente de facturar: avise a su supervisor.";
            case OTRO -> "El certificador rechazó la factura" + (detalle.isEmpty() ? "" : " (" + detalle + ")")
                    + ". La venta queda pendiente de facturar: avise a su supervisor.";
        };
    }

    /** Por el comienzo de la palabra: "monto" vale para "montos", pero "iva" no vale dentro de "inactiva". */
    private static boolean contiene(String texto, List<String> palabras) {
        return palabras.stream().anyMatch(palabra -> Pattern.compile("\\b" + Pattern.quote(palabra)).matcher(texto)
                .find());
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
