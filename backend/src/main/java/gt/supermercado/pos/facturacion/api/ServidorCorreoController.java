package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.GestionServidorCorreo;
import gt.supermercado.pos.facturacion.aplicacion.GestionServidorCorreo.SolicitudServidorCorreo;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionCorreo;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
import gt.supermercado.pos.facturacion.dominio.SeguridadCorreo;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** HU-08.15: el servidor de correo con que se envían las facturas, para el Administrador. */
@RestController
@RequestMapping("/api/configuracion/correo")
public class ServidorCorreoController {

    private final GestionServidorCorreo gestion;

    public ServidorCorreoController(GestionServidorCorreo gestion) {
        this.gestion = gestion;
    }

    /** El servidor tal como se muestra: {@code secretoGuardado} dice si hay contraseña, nunca cuál es. */
    public record RespuestaServidorCorreo(boolean configurado, String servidor, Integer puerto,
                                          SeguridadCorreo seguridad, String usuario, boolean secretoGuardado,
                                          String remitente, String nombreRemitente, Integer timeoutMs,
                                          OffsetDateTime modificadoEn) {

        static RespuestaServidorCorreo de(ConfiguracionCorreo config) {
            return new RespuestaServidorCorreo(true, config.servidor(), config.puerto(), config.seguridad(),
                    config.usuario(), config.secretoCifrado() != null, config.remitente(), config.nombreRemitente(),
                    config.timeoutMs(), config.modificadoEn());
        }

        static RespuestaServidorCorreo sinConfigurar() {
            return new RespuestaServidorCorreo(false, null, null, null, null, false, null, null, null, null);
        }
    }

    /** Los datos del formulario y a qué correo enviar la prueba. */
    public record SolicitudPrueba(SolicitudServidorCorreo servidor, String destinatario) {}

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaServidorCorreo obtener() {
        return gestion.configuracion().map(RespuestaServidorCorreo::de)
                .orElseGet(RespuestaServidorCorreo::sinConfigurar);
    }

    /** 422 si un dato no es válido. */
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaServidorCorreo guardar(@RequestBody SolicitudServidorCorreo solicitud,
                                           @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaServidorCorreo.de(gestion.guardar(solicitud, sesion));
    }

    /** Envía un correo de prueba con los datos del formulario, sin guardarlos. */
    @PostMapping("/probar")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public ResultadoPruebaConexion probar(@RequestBody SolicitudPrueba solicitud) {
        return gestion.probar(solicitud.servidor() == null
                ? new SolicitudServidorCorreo(null, null, null, null, null, null, null, null)
                : solicitud.servidor(), solicitud.destinatario());
    }
}
