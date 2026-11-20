package gt.supermercado.pos.compartido.dominio;

import java.util.List;

/**
 * Deja constancia de una acción rechazada por falta de permiso (HU-01.11 crit. 2, HU-11.04).
 * Lo implementa {@code seguridad}, que sabe quién tiene la sesión, para que {@code compartido} no dependa de ella.
 */
public interface RegistroAccesoDenegado {

    /**
     * @param metodo   método HTTP de la petición rechazada
     * @param ruta     ruta pedida, sin parámetros
     * @param permisos HU-11.04: los permisos que exige la acción (cualquiera de ellos bastaba); vacío si no se conocen
     */
    void registrar(String metodo, String ruta, List<String> permisos);
}
