package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.GestionConexionFel;
import gt.supermercado.pos.facturacion.aplicacion.GestionConexionFel.SolicitudConexionFel;
import gt.supermercado.pos.facturacion.dominio.AmbienteFel;
import gt.supermercado.pos.facturacion.dominio.ConfiguracionFel;
import gt.supermercado.pos.facturacion.dominio.ResultadoPruebaConexion;
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

/**
 * HU-08.01, HU-12.03 y HU-12.15: la conexión con el certificador FEL. Solo para quien puede modificar la
 * configuración (el Administrador, HU-12.15 crit. 3). La llave o token nunca sale del servidor.
 */
@RestController
@RequestMapping("/api/configuracion/fel")
public class ConexionFelController {

    private final GestionConexionFel gestion;

    public ConexionFelController(GestionConexionFel gestion) {
        this.gestion = gestion;
    }

    /** La conexión tal como se muestra: {@code secretoGuardado} dice si hay llave, nunca cuál es (HU-12.15 crit. 2). */
    public record RespuestaConexionFel(boolean configurada, String certificador, String urlServicio, String usuario,
                                       boolean secretoGuardado, AmbienteFel ambiente, Integer timeoutMs,
                                       OffsetDateTime modificadoEn) {

        static RespuestaConexionFel de(ConfiguracionFel config) {
            return new RespuestaConexionFel(true, config.certificador(), config.urlServicio(), config.usuario(), true,
                    config.ambiente(), config.timeoutMs(), config.modificadoEn());
        }

        static RespuestaConexionFel sinConfigurar() {
            return new RespuestaConexionFel(false, null, null, null, false, null, null, null);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaConexionFel obtener() {
        return gestion.configuracion().map(RespuestaConexionFel::de).orElseGet(RespuestaConexionFel::sinConfigurar);
    }

    /**
     * Guarda la conexión. 422 si un dato no es válido ({@code URL_NO_SEGURA} si no es https) o si pasa a producción
     * sin confirmarlo ({@code CONFIRMAR_PRODUCCION}).
     */
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaConexionFel guardar(@RequestBody SolicitudConexionFel solicitud,
                                        @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaConexionFel.de(gestion.guardar(solicitud, sesion));
    }

    /** Prueba los datos del formulario sin guardarlos ni emitir facturas (HU-12.03 crit. 2, HU-08.01 crit. 3). */
    @PostMapping("/probar")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public ResultadoPruebaConexion probar(@RequestBody SolicitudConexionFel solicitud) {
        return gestion.probar(solicitud);
    }
}
