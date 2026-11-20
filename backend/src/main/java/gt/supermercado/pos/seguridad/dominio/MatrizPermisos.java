package gt.supermercado.pos.seguridad.dominio;

import java.util.List;
import java.util.Map;

/**
 * HU-01.10 crit. 1: los permisos agrupados por módulo, con lo que tiene cada rol. Solo el Supervisor y el Cajero se
 * configuran (crit. 2); el Administrador los tiene todos y no se le quitan (crit. 3); los exclusivos del Administrador
 * no se asignan a otros roles (crit. 4).
 */
public record MatrizPermisos(List<Modulo> modulos, List<Rol> rolesConfigurables) {

    /** Crit. 1: los nombres de los módulos tal como los lee el Administrador. */
    public static final Map<String, String> NOMBRES_DE_MODULO = Map.ofEntries(
            Map.entry("catalogo", "Catálogo de productos"),
            Map.entry("ventas", "Ventas"),
            Map.entry("clientes", "Clientes"),
            Map.entry("pagos", "Pagos"),
            Map.entry("facturacion", "Facturación"),
            Map.entry("caja", "Caja"),
            Map.entry("reportes", "Reportes"),
            Map.entry("bitacora", "Bitácora"),
            Map.entry("configuracion", "Configuración"),
            Map.entry("seguridad", "Usuarios"));

    /** El orden de los módulos en pantalla. */
    public static final List<String> ORDEN_DE_MODULOS = List.of("catalogo", "ventas", "clientes", "pagos",
            "facturacion", "caja", "reportes", "bitacora", "configuracion", "seguridad");

    public record Modulo(String codigo, String nombre, List<Permiso> permisos) {}

    /** Un permiso y si lo tiene cada rol; el Administrador siempre lo tiene. */
    public record Permiso(String codigo, String descripcion, boolean exclusivoAdmin, boolean supervisor,
                          boolean cajero) {}
}
