package gt.supermercado.pos.compartido.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HU-11.04: los permisos que nombra la regla de un endpoint ({@code @PreAuthorize}), como
 * {@code hasAuthority('FACTURA_ANULAR') or hasAuthority('CAJA_VER_CIERRES')}, para registrar cuál faltó.
 */
public final class PermisosExigidos {

    private static final Pattern PERMISO = Pattern.compile("hasAuthority\\('([A-Z0-9_]+)'\\)");

    private PermisosExigidos() {
    }

    public static List<String> de(String regla) {
        List<String> permisos = new ArrayList<>();
        if (regla == null) {
            return permisos;
        }
        Matcher encontrado = PERMISO.matcher(regla);
        while (encontrado.find()) {
            permisos.add(encontrado.group(1));
        }
        return permisos;
    }
}
