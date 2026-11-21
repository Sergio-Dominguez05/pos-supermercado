package gt.supermercado.pos.bitacora.dominio;

import java.util.Map;

/**
 * Un registro de la bitácora (HU-11.02). La fecha y hora la pone PostgreSQL, no quien registra.
 * La descripción nunca debe contener contraseñas, PIN ni números de tarjeta.
 *
 * @param usuarioId {@code null} cuando el actor no existe (por ejemplo, un usuario mal escrito) o es el sistema
 * @param cambios   valor anterior y nuevo de cada campo modificado (HU-11.03), o {@code null};
 *                  ver {@link #cambio(Object, Object)} y {@link #MODIFICADO}
 * @param cajaId    caja donde ocurrió (HU-11.02 crit. 1). Quien registra normalmente la deja en {@code null}: el
 *                  servicio de bitácora la completa con el {@link ContextoEvento}
 */
public record EventoBitacora(
        Long usuarioId,
        String rol,
        String modulo,
        String accion,
        boolean exitoso,
        String descripcion,
        String entidad,
        Long entidadId,
        Map<String, Object> cambios,
        Long cajaId) {

    /** Valor que se registra en lugar de un dato sensible, como una contraseña o un PIN (HU-11.03). */
    public static final String MODIFICADO = "modificado";

    /**
     * HU-11.03 crit. 2: los campos de {@code cambios} cuyo valor nunca se guarda. Cuenta el nombre exacto, sin
     * mayúsculas, o seguido de otra palabra en camelCase ({@code secretoFel}, {@code pinTemporal}); no los códigos con
     * guion bajo, como el permiso {@code CREDENCIAL_RESTABLECER}, que no son datos sensibles.
     */
    public static final java.util.List<String> CAMPOS_SENSIBLES = java.util.List.of("secreto", "credencial",
            "contrasena", "contraseña", "password", "pin", "token", "clave", "llave");

    /**
     * HU-11.03 crit. 2, como red de seguridad: aunque un caso de uso pase el valor de un dato sensible, se guarda
     * {@link #MODIFICADO} en su lugar. Cada historia ya lo hace así; esto evita que un descuido lo filtre.
     */
    public EventoBitacora {
        cambios = enmascarar(cambios);
    }

    /** Los mismos cambios, con {@link #MODIFICADO} en cada campo sensible; {@code null} sigue siendo {@code null}. */
    static Map<String, Object> enmascarar(Map<String, Object> cambios) {
        if (cambios == null || cambios.keySet().stream().noneMatch(EventoBitacora::esSensible)) {
            return cambios;
        }
        Map<String, Object> seguros = new java.util.LinkedHashMap<>();
        cambios.forEach((campo, valor) -> seguros.put(campo, esSensible(campo) ? MODIFICADO : valor));
        return java.util.Collections.unmodifiableMap(seguros);
    }

    /** Si el campo es un dato sensible (HU-11.03 crit. 2). */
    public static boolean esSensible(String campo) {
        if (campo == null) {
            return false;
        }
        String nombre = campo.toLowerCase(java.util.Locale.ROOT);
        return CAMPOS_SENSIBLES.stream().anyMatch(sensible -> nombre.equals(sensible)
                || nombre.startsWith(sensible) && Character.isUpperCase(campo.charAt(sensible.length())));
    }

    /**
     * Un evento de una de las {@link AccionSensible acciones del catálogo} (HU-11.01): el módulo y el código de la
     * acción salen del catálogo. Es la forma de registrar que deben usar los casos de uso.
     */
    public EventoBitacora(Long usuarioId, String rol, AccionSensible accion, boolean exitoso,
                          String descripcion, String entidad, Long entidadId, Map<String, Object> cambios) {
        this(usuarioId, rol, accion.modulo(), accion.codigo(), exitoso, descripcion, entidad, entidadId, cambios, null);
    }

    public EventoBitacora(Long usuarioId, String rol, AccionSensible accion, boolean exitoso,
                          String descripcion, String entidad, Long entidadId) {
        this(usuarioId, rol, accion, exitoso, descripcion, entidad, entidadId, null);
    }

    public static EventoBitacora de(Long usuarioId, String rol, AccionSensible accion,
                                    boolean exitoso, String descripcion) {
        return new EventoBitacora(usuarioId, rol, accion, exitoso, descripcion, null, null);
    }

    /** Un campo modificado: {@code {"antes": ..., "despues": ...}}. */
    public static Map<String, Object> cambio(Object antes, Object despues) {
        return Map.of("antes", antes, "despues", despues);
    }

    /** El mismo evento, ocurrido en la caja indicada. */
    public EventoBitacora enCaja(Long caja) {
        return new EventoBitacora(usuarioId, rol, modulo, accion, exitoso, descripcion, entidad, entidadId, cambios, caja);
    }
}
