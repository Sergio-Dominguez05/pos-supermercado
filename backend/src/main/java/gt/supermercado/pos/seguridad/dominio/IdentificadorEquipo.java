package gt.supermercado.pos.seguridad.dominio;

import java.util.Optional;

/**
 * Identifica la caja a la que está vinculado el equipo desde el que se inicia sesión (HU-12.05 crit. 4,
 * Architecture.md §6.5). Lo implementa {@code configuracion}, que registra las cajas y depende de {@code seguridad}.
 */
public interface IdentificadorEquipo {

    /** Nombre de la cookie HttpOnly con el token del equipo. */
    String COOKIE = "POS_EQUIPO";

    /** @return la caja activa vinculada al token, o vacío si el equipo no está vinculado o la caja está inactiva */
    Optional<CajaDelEquipo> cajaActiva(byte[] tokenHash);

    record CajaDelEquipo(long id, String nombre) {}
}
