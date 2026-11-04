package gt.supermercado.pos.bitacora.dominio;

import java.util.Map;

/**
 * Un registro de la bitácora (HU-11.02). La fecha y hora la pone PostgreSQL, no quien registra.
 * La descripción nunca debe contener contraseñas, PIN ni números de tarjeta.
 *
 * @param usuarioId {@code null} cuando el actor no existe (por ejemplo, un usuario mal escrito) o es el sistema
 * @param cambios   valor anterior y nuevo de cada campo modificado (HU-11.03), o {@code null};
 *                  ver {@link #cambio(Object, Object)} y {@link #MODIFICADO}
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
        Map<String, Object> cambios) {

    /** Valor que se registra en lugar de un dato sensible, como una contraseña o un PIN (HU-11.03). */
    public static final String MODIFICADO = "modificado";

    public EventoBitacora(Long usuarioId, String rol, String modulo, String accion, boolean exitoso,
                          String descripcion, String entidad, Long entidadId) {
        this(usuarioId, rol, modulo, accion, exitoso, descripcion, entidad, entidadId, null);
    }

    public static EventoBitacora de(Long usuarioId, String rol, String modulo, String accion,
                                    boolean exitoso, String descripcion) {
        return new EventoBitacora(usuarioId, rol, modulo, accion, exitoso, descripcion, null, null);
    }

    /** Un campo modificado: {@code {"antes": ..., "despues": ...}}. */
    public static Map<String, Object> cambio(Object antes, Object despues) {
        return Map.of("antes", antes, "despues", despues);
    }
}
