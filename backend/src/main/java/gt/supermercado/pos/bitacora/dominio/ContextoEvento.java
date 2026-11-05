package gt.supermercado.pos.bitacora.dominio;

/**
 * Dónde ocurre el evento que se está registrando (HU-11.02 crit. 1). Lo implementa {@code seguridad}, que conoce la
 * sesión y el equipo; así ningún caso de uso tiene que pasar la caja a mano.
 */
public interface ContextoEvento {

    /** Sin contexto: fuera de una petición (al arrancar el sistema) o en pruebas unitarias. */
    ContextoEvento NINGUNO = () -> null;

    /** @return la caja donde ocurre el evento, o {@code null} si no ocurre en una caja */
    Long cajaActual();
}
