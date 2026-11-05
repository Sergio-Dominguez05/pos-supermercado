package gt.supermercado.pos.seguridad.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.NegocioException;
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
 * Cambio de la contraseña o el PIN propio (HU-01.13). El tipo de credencial sale del rol actual guardado, no del
 * de la sesión, porque es el que corresponde al hash. La credencial elegida por el usuario deja de ser temporal.
 */
@Service
public class CambioCredencial {

    static final AccionSensible ACCION = AccionSensible.CREDENCIAL_CAMBIADA;

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final Bitacora bitacora;

    public CambioCredencial(RepositorioUsuarios usuarios, PasswordEncoder codificador, Bitacora bitacora) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.bitacora = bitacora;
    }

    @Transactional
    public void cambiar(SesionActiva sesion, String actual, String nueva, String confirmacion) {
        Usuario usuario = usuarios.buscarPorId(sesion.usuarioId()).orElseThrow();
        TipoCredencial tipo = TipoCredencial.de(usuario.rol());
        boolean esPin = tipo == TipoCredencial.PIN;

        if (!nueva.equals(confirmacion)) {
            throw rechazar(sesion, "CONFIRMACION_DIFERENTE", esPin
                    ? "La confirmación no coincide con el nuevo PIN."
                    : "La confirmación no coincide con la nueva contraseña.");
        }
        if (!codificador.matches(actual, usuario.credencialHash())) {
            throw rechazar(sesion, "CREDENCIAL_ACTUAL_INCORRECTA", esPin
                    ? "El PIN actual es incorrecto."
                    : "La contraseña actual es incorrecta.");
        }
        if (!ReglasCredencial.esCredencialValida(tipo, nueva)) {
            throw rechazar(sesion, ReglasCredencial.codigoCredencialInvalida(tipo), esPin
                    ? "El nuevo PIN debe ser numérico, de 4 a 6 dígitos."
                    : "La nueva contraseña debe tener al menos 8 caracteres, con letras y números.");
        }
        if (codificador.matches(nueva, usuario.credencialHash())) {
            throw rechazar(sesion, "IGUAL_A_LA_ACTUAL", esPin
                    ? "El nuevo PIN debe ser distinto del actual."
                    : "La nueva contraseña debe ser distinta de la actual.");
        }

        usuarios.cambiarCredencial(usuario.id(), codificador.encode(nueva));
        bitacora.registrar(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), ACCION, true,
                "Cambio de " + tipo.nombre() + " propio", "usuario", usuario.id(),
                Map.of("credencial", EventoBitacora.MODIFICADO)));
    }

    /** El intento rechazado queda en la bitácora sin ningún valor de credencial (crit. 4). */
    private NegocioException rechazar(SesionActiva sesion, String codigo, String mensaje) {
        bitacora.registrarIntentoFallido(new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), ACCION,
                false, "Cambio de credencial rechazado: " + mensaje, "usuario", sesion.usuarioId()));
        return new NegocioException(codigo, mensaje);
    }
}
