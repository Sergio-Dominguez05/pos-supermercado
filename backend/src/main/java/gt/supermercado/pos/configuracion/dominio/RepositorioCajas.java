package gt.supermercado.pos.configuracion.dominio;

import java.util.List;
import java.util.Optional;

/** Cajas de la sucursal Matriz (V1 tiene una sola sucursal). */
public interface RepositorioCajas {

    List<Caja> listar();

    Optional<Caja> buscarPorId(long id);

    /** Caja (activa o no) cuyo equipo vinculado tiene este token. */
    Optional<Caja> buscarPorEquipo(byte[] tokenHash);

    /** Sin distinguir mayúsculas; {@code excluirId} permite renombrar una caja con su propio nombre. */
    boolean existeNombre(String nombre, Long excluirId);

    long crear(String nombre, String serieFel);

    void actualizar(long id, String nombre, String serieFel);

    void cambiarActivo(long id, boolean activo);

    /** Reemplaza el equipo vinculado: el anterior, si había, deja de estar asociado a la caja. */
    void vincularEquipo(long id, byte[] tokenHash);
}
