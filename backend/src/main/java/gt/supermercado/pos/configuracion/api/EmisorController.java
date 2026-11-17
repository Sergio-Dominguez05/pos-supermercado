package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionEmisor;
import gt.supermercado.pos.configuracion.aplicacion.GestionEmisor.SolicitudEmisor;
import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** HU-12.01: los datos fiscales del emisor. Solo para quien puede modificar la configuración (HU-12.15 crit. 3). */
@RestController
@RequestMapping("/api/configuracion/emisor")
public class EmisorController {

    private final GestionEmisor gestion;

    public EmisorController(GestionEmisor gestion) {
        this.gestion = gestion;
    }

    public record RespuestaEmisor(boolean configurado, String nit, String razonSocial, String nombreComercial,
                                  String direccionFiscal, String telefono, String correo, OffsetDateTime modificadoEn) {

        static RespuestaEmisor de(Emisor emisor) {
            return new RespuestaEmisor(true, emisor.nit(), emisor.razonSocial(), emisor.nombreComercial(),
                    emisor.direccionFiscal(), emisor.telefono(), emisor.correo(), emisor.modificadoEn());
        }

        static RespuestaEmisor sinConfigurar() {
            return new RespuestaEmisor(false, null, null, null, null, null, null, null);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaEmisor obtener() {
        return gestion.emisor().map(RespuestaEmisor::de).orElseGet(RespuestaEmisor::sinConfigurar);
    }

    /** Guarda los datos. 422 {@code NIT_INVALIDO} si el dígito verificador no corresponde (crit. 2). */
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public RespuestaEmisor guardar(@RequestBody SolicitudEmisor solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaEmisor.de(gestion.guardar(solicitud, sesion));
    }
}
