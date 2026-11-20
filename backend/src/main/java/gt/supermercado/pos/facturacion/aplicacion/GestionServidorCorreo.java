package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.bitacora.aplicacion.Bitacora;
import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import gt.supermercado.pos.compartido.dominio.CifradorSecretos;
import gt.supermercado.pos.compartido.dominio.Correo;
import gt.supermercado.pos.compartido.dominio.NegocioException;
import gt.supermercado.pos.facturacion.dominio.CanalCorreo;
import gt.supermercado.pos.facturacion.dominio.ConexionCorreo;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionCorreo;
import gt.supermercado.pos.facturacion.dominio.CorreoNoEnviadoException;
import gt.supermercado.pos.facturacion.dominio.MensajeCorreo;
import gt.supermercado.pos.facturacion.dominio.RepositorioConfiguracionCorreo;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
import gt.supermercado.pos.facturacion.dominio.SeguridadCorreo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * HU-08.15: el servidor de correo (SMTP) con que el POS envía las facturas, decisión de Sergio frente a que las envíe
 * el certificador. Como la conexión FEL (HU-12.15): la contraseña se guarda cifrada, nunca se devuelve y cada cambio
 * queda en la bitácora.
 */
@Service
public class GestionServidorCorreo {

    static final int TIMEOUT_POR_DEFECTO_MS = 10_000;

    private final RepositorioConfiguracionCorreo repositorio;
    private final CifradorSecretos cifrador;
    private final CanalCorreo canal;
    private final Bitacora bitacora;

    public GestionServidorCorreo(RepositorioConfiguracionCorreo repositorio, CifradorSecretos cifrador,
                                 CanalCorreo canal, Bitacora bitacora) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
        this.canal = canal;
        this.bitacora = bitacora;
    }

    /**
     * Lo que pide el Administrador. {@code usuario} vacío es un servidor sin autenticación; {@code secreto} vacío
     * conserva la contraseña guardada. {@code puerto} vacío usa el usual de la seguridad elegida.
     */
    public record SolicitudServidorCorreo(String servidor, Integer puerto, SeguridadCorreo seguridad, String usuario,
                                          String secreto, String remitente, String nombreRemitente,
                                          Integer timeoutMs) {

        @Override
        public String toString() {
            return "SolicitudServidorCorreo[servidor=%s, puerto=%s, seguridad=%s, usuario=%s, remitente=%s]"
                    .formatted(servidor, puerto, seguridad, usuario, remitente);
        }
    }

    @Transactional(readOnly = true)
    public Optional<ConfiguracionCorreo> configuracion() {
        return repositorio.configuracion();
    }

    /** La conexión guardada con la contraseña descifrada, para enviar. Vacío si todavía no se configuró. */
    @Transactional(readOnly = true)
    public Optional<ConexionCorreo> conexionVigente() {
        return repositorio.configuracion().map(guardada -> new ConexionCorreo(guardada.servidor(), guardada.puerto(),
                guardada.seguridad(), guardada.usuario(),
                guardada.secretoCifrado() == null ? null : cifrador.descifrar(guardada.secretoCifrado()),
                guardada.remitente(), guardada.nombreRemitente(), guardada.timeoutMs()));
    }

    /** Prueba los datos del formulario, guardados o no, enviando un correo de prueba a {@code destinatario}. */
    @Transactional(readOnly = true)
    public ResultadoPruebaConexion probar(SolicitudServidorCorreo solicitud, String destinatario) {
        ConexionCorreo conexion = validar(solicitud, repositorio.configuracion());
        String correo = Correo.opcional(destinatario, "DESTINATARIO_INVALIDO").orElseThrow(() -> new NegocioException(
                "DESTINATARIO_REQUERIDO", "Indique a qué correo enviar la prueba."));
        try {
            canal.enviar(conexion, new MensajeCorreo(correo, "Prueba del servidor de correo del POS",
                    "Este correo confirma que el POS puede enviar las facturas electrónicas por correo.", List.of()));
            return ResultadoPruebaConexion.exitosa("Se envió el correo de prueba a %s.".formatted(correo));
        } catch (CorreoNoEnviadoException e) {
            return ResultadoPruebaConexion.fallida(e.getMessage());
        }
    }

    /** Guarda el servidor con la contraseña cifrada y registra el cambio en la bitácora. */
    @Transactional
    public ConfiguracionCorreo guardar(SolicitudServidorCorreo solicitud, SesionActiva sesion) {
        Optional<ConfiguracionCorreo> anterior = repositorio.configuracion();
        ConexionCorreo conexion;
        try {
            conexion = validar(solicitud, anterior);
        } catch (NegocioException error) {
            bitacora.registrarIntentoFallido(evento(sesion, false,
                    "Cambio del servidor de correo rechazado: " + error.getMessage(), null));
            throw error;
        }
        boolean secretoNuevo = conexion.usuario() != null && !vacio(solicitud.secreto());
        byte[] secretoCifrado = conexion.usuario() == null ? null
                : secretoNuevo ? cifrador.cifrar(conexion.secreto()) : anterior.orElseThrow().secretoCifrado();
        repositorio.guardar(conexion.servidor(), conexion.puerto(), conexion.seguridad(), conexion.usuario(),
                secretoCifrado, conexion.remitente(), conexion.nombreRemitente(), conexion.timeoutMs(),
                sesion.usuarioId());
        Map<String, Object> cambios = cambios(anterior.orElse(null), conexion, secretoNuevo);
        bitacora.registrar(evento(sesion, true, "Servidor de correo %s: %s".formatted(
                anterior.isPresent() ? "modificado" : "configurado",
                cambios.isEmpty() ? "sin cambios" : String.join(", ", cambios.keySet())), cambios));
        return repositorio.configuracion().orElseThrow();
    }

    private ConexionCorreo validar(SolicitudServidorCorreo solicitud, Optional<ConfiguracionCorreo> guardada) {
        String servidor = texto(solicitud.servidor());
        if (servidor.isEmpty() || servidor.length() > 255 || servidor.contains(" ")) {
            throw new NegocioException("SERVIDOR_CORREO_REQUERIDO",
                    "Indique el servidor de correo, por ejemplo smtp.correo.com.");
        }
        if (solicitud.seguridad() == null) {
            throw new NegocioException("SEGURIDAD_REQUERIDA", "Indique la seguridad de la conexión: STARTTLS, SSL o "
                    + "ninguna.");
        }
        int puerto = solicitud.puerto() == null ? puertoUsual(solicitud.seguridad()) : solicitud.puerto();
        if (puerto < 1 || puerto > 65_535) {
            throw new NegocioException("PUERTO_INVALIDO", "El puerto debe estar entre 1 y 65535.");
        }
        String usuario = texto(solicitud.usuario());
        String secreto = null;
        if (usuario.isEmpty()) {
            usuario = null;
        } else if (usuario.length() > 254) {
            throw new NegocioException("USUARIO_CORREO_INVALIDO", "El usuario puede tener hasta 254 caracteres.");
        } else if (vacio(solicitud.secreto())) {
            secreto = guardada.filter(g -> g.secretoCifrado() != null).map(g -> cifrador.descifrar(g.secretoCifrado()))
                    .orElseThrow(() -> new NegocioException("SECRETO_REQUERIDO",
                            "Ingrese la contraseña del usuario del servidor de correo."));
        } else {
            secreto = solicitud.secreto();
        }
        String remitente = Correo.opcional(solicitud.remitente(), "REMITENTE_INVALIDO").orElseThrow(() ->
                new NegocioException("REMITENTE_REQUERIDO", "Indique el correo desde el que salen las facturas."));
        String nombre = texto(solicitud.nombreRemitente());
        if (nombre.length() > 100) {
            throw new NegocioException("NOMBRE_REMITENTE_INVALIDO", "El nombre del remitente puede tener hasta 100 "
                    + "caracteres.");
        }
        int timeout = solicitud.timeoutMs() == null ? TIMEOUT_POR_DEFECTO_MS : solicitud.timeoutMs();
        if (timeout < 1_000 || timeout > 60_000) {
            throw new NegocioException("TIEMPO_LIMITE_INVALIDO", "El tiempo límite debe estar entre 1 y 60 segundos.");
        }
        return new ConexionCorreo(servidor, puerto, solicitud.seguridad(), usuario, secreto, remitente,
                nombre.isEmpty() ? null : nombre, timeout);
    }

    private static int puertoUsual(SeguridadCorreo seguridad) {
        return switch (seguridad) {
            case STARTTLS -> 587;
            case SSL -> 465;
            case NINGUNA -> 25;
        };
    }

    /** Los campos que cambiaron; de la contraseña solo se dice que cambió ({@link EventoBitacora#MODIFICADO}). */
    private static Map<String, Object> cambios(ConfiguracionCorreo antes, ConexionCorreo despues,
                                               boolean secretoNuevo) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambio(cambios, "servidor", antes == null ? null : antes.servidor(), despues.servidor());
        cambio(cambios, "puerto", antes == null ? null : antes.puerto(), despues.puerto());
        cambio(cambios, "seguridad", antes == null ? null : antes.seguridad().name(), despues.seguridad().name());
        cambio(cambios, "usuario", antes == null ? null : antes.usuario(), despues.usuario());
        cambio(cambios, "remitente", antes == null ? null : antes.remitente(), despues.remitente());
        cambio(cambios, "nombreRemitente", antes == null ? null : antes.nombreRemitente(), despues.nombreRemitente());
        cambio(cambios, "timeoutMs", antes == null ? null : antes.timeoutMs(), despues.timeoutMs());
        if (secretoNuevo) {
            cambios.put("secreto", EventoBitacora.MODIFICADO);
        }
        return cambios;
    }

    private static void cambio(Map<String, Object> cambios, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.put(campo, EventoBitacora.cambio(antes == null ? "" : antes, despues == null ? "" : despues));
        }
    }

    private static EventoBitacora evento(SesionActiva sesion, boolean exitoso, String descripcion,
                                         Map<String, Object> cambios) {
        return new EventoBitacora(sesion.usuarioId(), sesion.rol().name(), AccionSensible.PARAMETRO_MODIFICADO,
                exitoso, descripcion, "config_correo", 1L, cambios);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
