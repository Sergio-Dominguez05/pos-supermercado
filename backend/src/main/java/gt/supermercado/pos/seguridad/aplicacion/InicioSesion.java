package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.seguridad.dominio.CredencialesInvalidasException;
import gt.supermercado.pos.seguridad.dominio.RepositorioSesiones;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TokenSesion;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Inicio de sesión con usuario y contraseña para Administrador y Supervisor (HU-01.01). */
@Service
public class InicioSesion {

    static final String MODULO = "seguridad";
    static final String ACCION = "INICIO_SESION";

    private final RepositorioUsuarios usuarios;
    private final RepositorioSesiones sesiones;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;
    /** Se compara contra este hash cuando el usuario no existe, para que el tiempo de respuesta no lo delate. */
    private final String hashFicticio;

    public InicioSesion(RepositorioUsuarios usuarios, RepositorioSesiones sesiones,
                        PasswordEncoder codificador, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.codificador = codificador;
        this.bitacora = bitacora;
        this.hashFicticio = codificador.encode(TokenSesion.generar());
    }

    public record SesionIniciada(String token, SesionActiva sesion) {}

    @Transactional
    public SesionIniciada conContrasena(String username, String contrasena) {
        Optional<Usuario> encontrado = usuarios.buscarPorUsername(username.strip());
        boolean coincide = codificador.matches(contrasena,
                encontrado.map(Usuario::credencialHash).orElse(hashFicticio));

        // El usuario escrito no se guarda: a veces se escribe ahí la contraseña por error (HU-11.02 crit. 4).
        Usuario usuario = encontrado.orElseThrow(() -> rechazar(null, "Intento con un usuario inexistente"));
        if (!coincide) {
            throw rechazar(usuario, "Contraseña incorrecta");
        }
        if (!usuario.activo()) {
            throw rechazar(usuario, "Usuario desactivado");
        }
        if (!usuario.rol().usaContrasena()) {
            throw rechazar(usuario, "El rol Cajero inicia sesión con PIN");
        }

        String token = TokenSesion.generar();
        byte[] tokenHash = TokenSesion.hash(token);
        sesiones.abrir(tokenHash, usuario.id());
        usuarios.registrarAcceso(usuario.id());
        bitacora.registrar(evento(usuario, true, "Inicio de sesión con contraseña"));
        return new SesionIniciada(token, sesiones.buscarActiva(tokenHash).orElseThrow());
    }

    private CredencialesInvalidasException rechazar(Usuario usuario, String motivo) {
        bitacora.registrarIntentoFallido(evento(usuario, false, "Inicio de sesión rechazado: " + motivo));
        return new CredencialesInvalidasException();
    }

    private static EventoBitacora evento(Usuario usuario, boolean exitoso, String descripcion) {
        return usuario == null
                ? new EventoBitacora(null, null, MODULO, ACCION, exitoso, descripcion, null, null)
                : new EventoBitacora(usuario.id(), usuario.rol().name(), MODULO, ACCION, exitoso, descripcion,
                        "usuario", usuario.id());
    }
}
