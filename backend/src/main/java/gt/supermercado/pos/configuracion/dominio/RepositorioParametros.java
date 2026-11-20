package gt.supermercado.pos.configuracion.dominio;

import java.math.BigDecimal;
import java.util.Optional;

/** Parámetros de operación guardados en la tabla {@code parametro}, por su clave. */
public interface RepositorioParametros {

    /** El valor entero del parámetro, si existe y es un número entero. */
    Optional<Integer> entero(String clave);

    /** El valor booleano del parámetro, si existe y tiene ese tipo. */
    Optional<Boolean> booleano(String clave);

    /** El valor decimal del parámetro (por ejemplo un monto en quetzales), si existe y es un número. */
    Optional<BigDecimal> decimal(String clave);

    void guardarEntero(String clave, int valor);

    void guardarDecimal(String clave, BigDecimal valor);

    void guardarBooleano(String clave, boolean valor);
}
