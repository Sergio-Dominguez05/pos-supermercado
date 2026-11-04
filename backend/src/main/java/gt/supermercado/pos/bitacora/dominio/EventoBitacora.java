package gt.supermercado.pos.bitacora.dominio;

/**
 * Un registro de la bitácora (HU-11.02). La fecha y hora la pone PostgreSQL, no quien registra.
 * La descripción nunca debe contener contraseñas, PIN ni números de tarjeta.
 *
 * @param usuarioId {@code null} cuando el actor no existe (por ejemplo, un usuario mal escrito) o es el sistema
 */
public record EventoBitacora(
        Long usuarioId,
        String rol,
        String modulo,
        String accion,
        boolean exitoso,
        String descripcion,
        String entidad,
        Long entidadId) {

    public static EventoBitacora de(Long usuarioId, String rol, String modulo, String accion,
                                    boolean exitoso, String descripcion) {
        return new EventoBitacora(usuarioId, rol, modulo, accion, exitoso, descripcion, null, null);
    }
}
