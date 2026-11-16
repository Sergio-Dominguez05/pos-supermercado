package gt.supermercado.pos.configuracion.dominio;

import java.util.Optional;

/** Parámetros de operación guardados en la tabla {@code parametro}, por su clave. */
public interface RepositorioParametros {

    /** El valor entero del parámetro, si existe y es un número entero. */
    Optional<Integer> entero(String clave);
}
