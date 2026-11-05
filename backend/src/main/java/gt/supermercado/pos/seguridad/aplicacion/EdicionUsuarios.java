package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.DirectorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ReglasCredencial;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.TipoCredencial;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Edición de usuarios por el Administrador (HU-01.07): nombre completo y rol. El nombre de usuario no cambia.
 * Al pasar entre Cajero (PIN) y Administrador o Supervisor (contraseña) se exige una credencial temporal nueva.
 * Las sesiones abiertas conservan su rol y permisos hasta el siguiente inicio de sesión.
 */
@Service
public class EdicionUsuarios {

    static final AccionSensible ACCION = AccionSensible.USUARIO_EDITADO;

    private final RepositorioUsuarios usuarios;
    private final DirectorioUsuarios directorio;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;

    public EdicionUsuarios(RepositorioUsuarios usuarios, DirectorioUsuarios directorio,
                           PasswordEncoder codificador, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.directorio = directorio;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    /** @param credencialTemporal solo se usa si el rol cambia de tipo de credencial; puede ser {@code null} */
    public record CambiosUsuario(String nombreCompleto, Rol rol, String credencialTemporal) {}

    @Transactional(readOnly = true)
    public ResumenUsuario obtener(long id) {
        return directorio.buscarPorId(id).orElseThrow(EdicionUsuarios::noEncontrado);
    }

    @Transactional
    public ResumenUsuario editar(long id, CambiosUsuario datos, SesionActiva actor) {
        Usuario actual = usuarios.buscarPorId(id).orElseThrow(EdicionUsuarios::noEncontrado);
        String nombreCompleto = datos.nombreCompleto().strip();
        TipoCredencial tipoNuevo = TipoCredencial.de(datos.rol());
        boolean cambiaCredencial = TipoCredencial.de(actual.rol()) != tipoNuevo;

        if (actual.rol() == Rol.ADMIN && datos.rol() != Rol.ADMIN && actual.activo()
                && usuarios.contarAdministradoresActivos() <= 1) {
            throw rechazar(actor, actual, "ULTIMO_ADMINISTRADOR",
                    "No se puede quitar el rol al último Administrador activo.");
        }
        String credencialHash = null;
        if (cambiaCredencial) {
            String credencial = datos.credencialTemporal() == null ? "" : datos.credencialTemporal();
            if (!ReglasCredencial.esCredencialValida(tipoNuevo, credencial)) {
                throw rechazar(actor, actual, ReglasCredencial.codigoCredencialInvalida(tipoNuevo),
                        ReglasCredencial.mensajeCredencialInvalida(tipoNuevo));
            }
            credencialHash = codificador.encode(credencial);
        }

        // Valor anterior y nuevo de cada campo modificado; la credencial nunca con su valor (HU-11.03).
        Map<String, Object> cambios = new LinkedHashMap<>();
        if (!nombreCompleto.equals(actual.nombreCompleto())) {
            cambios.put("nombreCompleto", EventoBitacora.cambio(actual.nombreCompleto(), nombreCompleto));
        }
        if (datos.rol() != actual.rol()) {
            cambios.put("rol", EventoBitacora.cambio(actual.rol().name(), datos.rol().name()));
        }
        if (cambiaCredencial) {
            cambios.put("credencial", EventoBitacora.MODIFICADO);
        }
        if (cambios.isEmpty()) {
            return obtener(id);
        }

        usuarios.actualizar(id, nombreCompleto, datos.rol(), credencialHash);
        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), ACCION, true,
                "Usuario '%s' editado: %s".formatted(actual.username(), String.join(", ", cambios.keySet())),
                "usuario", id, cambios));
        return obtener(id);
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO", "El usuario no existe.");
    }

    /** El intento rechazado queda en la bitácora sin la credencial (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva actor, Usuario usuario, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(), ACCION,
                false, "Edición del usuario '%s' rechazada: %s".formatted(usuario.username(), mensaje),
                "usuario", usuario.id()));
        return new NegocioException(codigo, mensaje);
    }
}
