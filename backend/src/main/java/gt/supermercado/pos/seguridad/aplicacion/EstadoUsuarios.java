package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.compartido.dominio.RecursoNoEncontradoException;
import gt.supermercado.pos.seguridad.dominio.DirectorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.RepositorioUsuarios;
import gt.supermercado.pos.seguridad.dominio.ResumenUsuario;
import gt.supermercado.pos.seguridad.dominio.Rol;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import gt.supermercado.pos.seguridad.dominio.Usuario;
import gt.supermercado.pos.seguridad.dominio.VerificadorCajaAbierta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Activación y desactivación de usuarios (HU-01.08). Un usuario desactivado no puede iniciar sesión y sus sesiones
 * abiertas dejan de valer en la siguiente petición; nunca se borra, así su historial se conserva.
 */
@Service
public class EstadoUsuarios {

    static final String MODULO = "seguridad";

    private final RepositorioUsuarios usuarios;
    private final DirectorioUsuarios directorio;
    private final VerificadorCajaAbierta cajas;
    private final Bitacora bitacora;

    public EstadoUsuarios(RepositorioUsuarios usuarios, DirectorioUsuarios directorio,
                          VerificadorCajaAbierta cajas, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.directorio = directorio;
        this.cajas = cajas;
        this.bitacora = bitacora;
    }

    @Transactional
    public ResumenUsuario desactivar(long id, SesionActiva actor) {
        Usuario usuario = usuarios.buscarPorId(id).orElseThrow(EstadoUsuarios::noEncontrado);
        if (!usuario.activo()) {
            return resumen(id);
        }
        if (usuario.id() == actor.usuarioId()) {
            throw rechazar(actor, usuario, "PROPIO_USUARIO", "No puede desactivar su propio usuario.");
        }
        if (usuario.rol() == Rol.ADMIN && usuarios.contarAdministradoresActivos() <= 1) {
            throw rechazar(actor, usuario, "ULTIMO_ADMINISTRADOR", "No se puede desactivar al último Administrador activo.");
        }
        if (cajas.tieneCajaAbierta(id)) {
            throw rechazar(actor, usuario, "CAJA_ABIERTA",
                    "El usuario tiene una caja abierta: primero se debe cerrar la caja.");
        }
        return cambiar(usuario, false, actor);
    }

    @Transactional
    public ResumenUsuario activar(long id, SesionActiva actor) {
        Usuario usuario = usuarios.buscarPorId(id).orElseThrow(EstadoUsuarios::noEncontrado);
        return usuario.activo() ? resumen(id) : cambiar(usuario, true, actor);
    }

    private ResumenUsuario cambiar(Usuario usuario, boolean activo, SesionActiva actor) {
        usuarios.cambiarActivo(usuario.id(), activo);
        bitacora.registrar(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO, accion(activo), true,
                "Usuario '%s' %s".formatted(usuario.username(), activo ? "activado" : "desactivado"),
                "usuario", usuario.id(), Map.of("activo", EventoBitacora.cambio(!activo, activo))));
        return resumen(usuario.id());
    }

    private ResumenUsuario resumen(long id) {
        return directorio.buscarPorId(id).orElseThrow(EstadoUsuarios::noEncontrado);
    }

    private static String accion(boolean activo) {
        return activo ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO";
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO", "El usuario no existe.");
    }

    /** El intento rechazado también queda en la bitácora (HU-11.01 crit. 3). */
    private NegocioException rechazar(SesionActiva actor, Usuario usuario, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(actor.usuarioId(), actor.rol().name(), MODULO,
                accion(false), false, "Desactivación del usuario '%s' rechazada: %s".formatted(usuario.username(), mensaje),
                "usuario", usuario.id()));
        return new NegocioException(codigo, mensaje);
    }
}
