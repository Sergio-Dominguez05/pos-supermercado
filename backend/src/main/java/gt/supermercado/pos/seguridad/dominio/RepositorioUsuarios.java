package gt.supermercado.pos.seguridad.dominio;

import java.util.Optional;

public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorUsername(String username);

    boolean existeAlgunoConRol(Rol rol);

    /** Crea el usuario activo y con credencial temporal. Devuelve su id. */
    long crear(String username, String nombreCompleto, Rol rol, String credencialHash);

    /** Guarda la hora del servidor como último acceso. */
    void registrarAcceso(long usuarioId);
}
