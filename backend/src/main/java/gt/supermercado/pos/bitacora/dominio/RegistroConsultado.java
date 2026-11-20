package gt.supermercado.pos.bitacora.dominio;

import java.time.OffsetDateTime;

/**
 * HU-11.06 crit. 1: un registro de la bitácora tal como se lista: fecha y hora, usuario, caja, módulo, acción y
 * resultado, con su descripción.
 *
 * @param usuario "Nombre (usuario)", o {@code null} si no hubo usuario
 */
public record RegistroConsultado(long id, long secuencia, OffsetDateTime ocurridoEn, String usuario, String rol,
                                 String caja, String modulo, String accion, boolean exitoso, String descripcion) {
}
