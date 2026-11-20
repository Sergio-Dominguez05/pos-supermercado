package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.MatrizPermisos;
import gt.supermercado.pos.seguridad.dominio.RepositorioPermisos;
import gt.supermercado.pos.seguridad.dominio.RepositorioPermisos.PermisoDelCatalogo;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-01.10 Configurar permisos por rol.
 * <ul>
 *   <li>Crit. 1: los permisos agrupados por módulo.</li>
 *   <li>Crit. 2 y 3: solo se configuran el Supervisor y el Cajero; el Administrador conserva todos.</li>
 *   <li>Crit. 4: los permisos exclusivos del Administrador no se asignan a otros roles (también lo impide la base de
 *       datos, V33).</li>
 *   <li>Crit. 5: la configuración inicial es la matriz de V2.</li>
 *   <li>Crit. 6: cada cambio queda en la bitácora con el valor anterior y el nuevo de cada permiso; también el intento
 *       rechazado.</li>
 * </ul>
 * Decisión de Sergio: como el cambio de rol (HU-01.07 crit. 4), un cambio de permisos se aplica desde el siguiente
 * inicio de sesión de cada usuario; las sesiones abiertas conservan los permisos con que empezaron.
 */
@Service
public class ConfiguracionPermisos {

    static final List<Rol> CONFIGURABLES = List.of(Rol.SUPERVISOR, Rol.CAJERO);

    private final RepositorioPermisos permisos;
    private final Bitacora bitacora;

    public ConfiguracionPermisos(RepositorioPermisos permisos, Bitacora bitacora) {
        this.permisos = permisos;
        this.bitacora = bitacora;
    }

    @Transactional(readOnly = true)
    public MatrizPermisos matriz() {
        Map<String, List<PermisoDelCatalogo>> porModulo = permisos.catalogo().stream()
                .collect(Collectors.groupingBy(PermisoDelCatalogo::modulo, LinkedHashMap::new, Collectors.toList()));
        List<MatrizPermisos.Modulo> modulos = new ArrayList<>();
        porModulo.keySet().stream()
                .sorted(Comparator.comparingInt(ConfiguracionPermisos::posicion))
                .forEach(modulo -> modulos.add(new MatrizPermisos.Modulo(modulo,
                        MatrizPermisos.NOMBRES_DE_MODULO.getOrDefault(modulo, modulo),
                        porModulo.get(modulo).stream().map(p -> new MatrizPermisos.Permiso(p.codigo(),
                                p.descripcion(), p.exclusivoAdmin(), p.roles().contains(Rol.SUPERVISOR),
                                p.roles().contains(Rol.CAJERO))).toList())));
        return new MatrizPermisos(List.copyOf(modulos), CONFIGURABLES);
    }

    private static int posicion(String modulo) {
        int indice = MatrizPermisos.ORDEN_DE_MODULOS.indexOf(modulo);
        return indice < 0 ? Integer.MAX_VALUE : indice;
    }

    /** Crit. 2 a 4 y 6: deja al rol con exactamente estos permisos. */
    @Transactional
    public MatrizPermisos cambiar(Rol rol, Set<String> elegidos, SesionActiva sesion) {
        Set<String> pedidos = elegidos == null ? Set.of() : new TreeSet<>(elegidos);
        Set<String> antes;
        try {
            if (!CONFIGURABLES.contains(rol)) {
                throw new NegocioException("ROL_NO_CONFIGURABLE", rol == Rol.ADMIN
                        ? "Los permisos del Administrador no se pueden reducir."
                        : "Solo se configuran los permisos del Supervisor y del Cajero.");
            }
            Map<String, PermisoDelCatalogo> catalogo = permisos.catalogo().stream()
                    .collect(Collectors.toMap(PermisoDelCatalogo::codigo, Function.identity()));
            for (String codigo : pedidos) {
                PermisoDelCatalogo permiso = catalogo.get(codigo);
                if (permiso == null) {
                    throw new NegocioException("PERMISO_INEXISTENTE", "El permiso %s no existe.".formatted(codigo));
                }
                if (permiso.exclusivoAdmin()) {
                    throw new NegocioException("PERMISO_EXCLUSIVO_ADMIN",
                            "\"%s\" es exclusivo del Administrador y no se puede asignar a otros roles."
                                    .formatted(permiso.descripcion()));
                }
            }
            antes = new TreeSet<>(permisos.delRol(rol));
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                    AccionSensible.PERMISOS_MODIFICADOS, false,
                    "Cambio de permisos del rol %s rechazado: %s".formatted(rol.name(), error.getMessage()), "rol",
                    (long) rol.id()));
            throw error;
        }
        if (antes.equals(pedidos)) {
            return matriz();
        }
        permisos.reemplazar(rol, pedidos);
        // HU-11.03: cada permiso agregado o quitado, con su valor anterior y el nuevo.
        Set<String> todos = new TreeSet<>(antes);
        todos.addAll(pedidos);
        Map<String, Object> cambios = new LinkedHashMap<>();
        for (String codigo : todos) {
            if (antes.contains(codigo) != pedidos.contains(codigo)) {
                cambios.put(codigo, EventoBitacora.cambio(antes.contains(codigo), pedidos.contains(codigo)));
            }
        }
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(),
                AccionSensible.PERMISOS_MODIFICADOS, true, "Permisos del rol %s modificados: %s".formatted(rol.name(),
                        cambios.keySet().stream().map(codigo -> (pedidos.contains(codigo) ? "+" : "-") + codigo)
                                .collect(Collectors.joining(", "))), "rol", (long) rol.id(), cambios));
        return matriz();
    }
}
