package gt.supermercado.pos.caja.dominio;

import java.time.OffsetDateTime;

/**
 * Turno de caja (HU-09.01): quién lo abrió, en qué caja y cuándo (crit. 2). No tiene fondo inicial porque no se
 * manejan pagos en efectivo (crit. 3).
 */
public record Turno(
        long id,
        long cajaId,
        String cajaNombre,
        long usuarioId,
        String usuarioNombre,
        EstadoTurno estado,
        OffsetDateTime abiertoEn) {

    public boolean esDe(long usuarioId) {
        return this.usuarioId == usuarioId;
    }
}
