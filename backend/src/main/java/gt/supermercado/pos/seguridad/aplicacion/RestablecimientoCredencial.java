package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.ReglasCredencial;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TipoCredencial;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Restablecimiento de la contraseña o el PIN de un usuario que lo olvidó (HU-01.14). El Administrador asigna una
 * credencial temporal nueva sin conocer la actual: el sistema solo guarda hashes y nunca los devuelve (crit. 3).
 */
@Service
public class RestablecimientoCredencial {

    static final String MODULO = "seguridad";
    static final String ACCION = "CREDENCIAL_RESTABLECIDA";

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;

    public RestablecimientoCredencial(RepositorioUsuarios usuarios, PasswordEncoder codificador, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    /** @return el tipo de credencial asignada, según el rol del usuario (crit. 1) */
    @Transactional
    public TipoCredencial restablecer(long id, String credencialTemporal, SesionActiva actor) {
        Usuario usuario = usuarios.buscarPorId(id).orElseThrow(
                () -> new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO", "El usuario no existe."));
        TipoCredencial tipo = TipoCredencial.de(usuario.rol());

        if (!ReglasCredencial.esCredencialValida(tipo, credencialTemporal)) {
            String mensaje = ReglasCredencial.mensajeCredencialInvalida(tipo);
            bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, ACCION,
                    false, "Restablecimiento de '%s' rechazado: %s".formatted(usuario.username(), mensaje),
                    "usuario", usuario.id()));
            throw new NegocioException(ReglasCredencial.codigoCredencialInvalida(tipo), mensaje);
        }

        usuarios.restablecerCredencial(usuario.id(), codificador.encode(credencialTemporal));
        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, ACCION, true,
                "Restablecimiento de %s de '%s'".formatted(tipo.nombre(), usuario.username()), "usuario", usuario.id(),
                Map.of("credencial", EventoBitacora.MODIFICADO)));
        return tipo;
    }
}
