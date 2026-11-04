package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.seguridad.dominio.ReglasCredencial;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer Administrador al arrancar si todavía no existe ninguno, para poder entrar al sistema
 * antes de que haya gestión de usuarios (HU-01.05). La contraseña sale de {@code POS_ADMIN_PASSWORD_INICIAL}
 * y queda marcada como temporal; nunca se guarda en el repositorio ni en las migraciones.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    static final String USERNAME = "admin";

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final String contrasena;
    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;

    public AdministradorInicial(@Value("${pos.seguridad.admin-inicial.contrasena:}") String contrasena,
                                RepositorioUsuarios usuarios,
                                PasswordEncoder codificador,
                                Bitacora bitacora) {
        this.contrasena = contrasena;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        if (usuarios.existeAlgunoConRol(Rol.ADMIN)) {
            return;
        }
        if (contrasena.isBlank()) {
            log.warn("No existe ningún Administrador y POS_ADMIN_PASSWORD_INICIAL está vacía: nadie podrá iniciar sesión.");
            return;
        }
        if (!ReglasCredencial.esContrasenaValida(contrasena)) {
            throw new IllegalStateException(
                    "POS_ADMIN_PASSWORD_INICIAL debe tener al menos 8 caracteres, con letras y números.");
        }

        long id = usuarios.crear(USERNAME, "Administrador", Rol.ADMIN, codificador.encode(contrasena));
        bitacora.registrar(new EventoBitacora(null, null, "seguridad", "USUARIO_CREADO", true,
                "Administrador inicial creado al arrancar el sistema", "usuario", id));
        log.info("Se creó el Administrador inicial '{}' con contraseña temporal.", USERNAME);
    }
}
