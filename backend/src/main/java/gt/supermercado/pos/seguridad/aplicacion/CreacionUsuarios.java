package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.seguridad.dominio.ReglasCredencial;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TipoCredencial;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios por el Administrador (HU-01.05). El usuario queda activo y con una credencial temporal:
 * contraseña para Administrador y Supervisor, PIN para Cajero. La credencial solo se guarda como hash Argon2id.
 */
@Service
public class CreacionUsuarios {

    static final String MODULO = "seguridad";
    static final String ACCION = "USUARIO_CREADO";

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;

    public CreacionUsuarios(RepositorioUsuarios usuarios, PasswordEncoder codificador, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    public record NuevoUsuario(String nombreCompleto, String username, Rol rol, String credencialTemporal) {}

    public record UsuarioCreado(long id, String username, String nombreCompleto, Rol rol,
                                boolean activo, boolean credencialTemporal) {}

    @Transactional
    public UsuarioCreado crear(NuevoUsuario datos, SesionActiva actor) {
        String username = ReglasCredencial.normalizarUsername(datos.username());
        String nombreCompleto = datos.nombreCompleto().strip();
        TipoCredencial tipo = TipoCredencial.de(datos.rol());

        if (!ReglasCredencial.esUsernameValido(username)) {
            throw rechazar(actor, username, "USERNAME_INVALIDO",
                    "El nombre de usuario debe tener de 3 a 30 caracteres: letras, números, punto, guion o guion bajo.");
        }
        if (!ReglasCredencial.esCredencialValida(tipo, datos.credencialTemporal())) {
            throw tipo == TipoCredencial.PIN
                    ? rechazar(actor, username, "PIN_INVALIDO", "El PIN temporal debe ser numérico, de 4 a 6 dígitos.")
                    : rechazar(actor, username, "CONTRASENA_INVALIDA",
                            "La contraseña temporal debe tener al menos 8 caracteres, con letras y números.");
        }
        if (usuarios.buscarPorUsername(username).isPresent()) {
            throw duplicado(actor, username);
        }

        long id;
        try {
            id = usuarios.crear(username, nombreCompleto, datos.rol(), codificador.encode(datos.credencialTemporal()));
        } catch (DuplicateKeyException e) {
            // Otro administrador lo creó entre la consulta y el INSERT; la restricción UNIQUE lo detecta.
            throw duplicado(actor, username);
        }

        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, ACCION, true,
                "Usuario '%s' creado con rol %s".formatted(username, datos.rol().name()), "usuario", id));
        return new UsuarioCreado(id, username, nombreCompleto, datos.rol(), true, true);
    }

    private NegocioException duplicado(SesionActiva actor, String username) {
        return rechazar(actor, username, "USERNAME_DUPLICADO", "El nombre de usuario ya existe.");
    }

    /** El intento fallido queda en la bitácora sin la credencial (HU-11.01 crit. 3, HU-11.02 crit. 4). */
    private NegocioException rechazar(SesionActiva actor, String username, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, ACCION,
                false, "Creación del usuario '%s' rechazada: %s".formatted(username, mensaje), null, null));
        return new NegocioException(codigo, mensaje);
    }
}
