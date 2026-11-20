package gt.supermercado.pos.bitacora.dominio;

import java.time.OffsetDateTime;

/**
 * Un cambio de un campo tal como quedó en la bitácora: cuándo, el valor anterior, el nuevo y quién lo hizo. Es de
 * solo lectura: la bitácora no se puede modificar (HU-11.05).
 *
 * @param usuario el nombre y el usuario de quien hizo el cambio, o {@code null} si fue el sistema
 */
public record CambioRegistrado(OffsetDateTime momento, String antes, String despues, String usuario) {
}
