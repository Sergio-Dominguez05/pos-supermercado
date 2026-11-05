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
