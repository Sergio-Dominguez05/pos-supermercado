package gt.supermercado.pos.configuracion.dominio;

import java.util.Optional;

/** Los establecimientos SAT, uno por sucursal (HU-12.02 crit. 3). */
public interface RepositorioEstablecimientos {

    /** La sucursal de V1 ("Matriz"). EP-13 agrega las demás y la elección de sucursal. */
    long sucursalPrincipal();

    Optional<Establecimiento> deLaSucursal(long sucursalId);

    /** Crea o reemplaza el establecimiento de la sucursal. Lanza si el código SAT ya es de otra sucursal. */
    void guardar(long sucursalId, String codigoSat, String nombre, String direccion, long usuarioId);
}
