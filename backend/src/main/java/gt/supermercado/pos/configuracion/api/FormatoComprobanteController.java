package gt.supermercado.pos.configuracion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionFormatoComprobante;
import gt.supermercado.pos.configuracion.aplicacion.GestionFormatoComprobante.SolicitudFormato;
import gt.supermercado.pos.configuracion.aplicacion.LogoMonocromo;
import gt.supermercado.pos.configuracion.dominio.FormatoComprobante;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * HU-12.09 Personalizar el formato del comprobante. Solo con el permiso de modificar la configuración (el
 * Administrador). La vista previa (crit. 3) está en {@code impresion.api}, que es quien arma el ticket.
 */
@RestController
@RequestMapping("/api/configuracion/comprobante")
public class FormatoComprobanteController {

    private final GestionFormatoComprobante gestion;

    public FormatoComprobanteController(GestionFormatoComprobante gestion) {
        this.gestion = gestion;
    }

    /** El formato como lo ve la pantalla: el logo en blanco y negro como imagen PNG ({@code data:} URL). */
    public record FormatoVisible(String encabezado, String pie, String logo, Integer logoAncho, Integer logoAlto,
                                 OffsetDateTime modificadoEn) {

        public static FormatoVisible de(FormatoComprobante formato) {
            return new FormatoVisible(formato.encabezado(), formato.pie(), LogoMonocromo.dataUrl(formato.logo()),
                    formato.logo() == null ? null : formato.logo().ancho(),
                    formato.logo() == null ? null : formato.logo().alto(), formato.modificadoEn());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public FormatoVisible actual() {
        return FormatoVisible.de(gestion.actual());
    }

    /** 422 si un mensaje es muy largo o tiene caracteres que no se imprimen, o si el logo no es una imagen. */
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public FormatoVisible guardar(@RequestBody SolicitudFormato solicitud, @AuthenticationPrincipal SesionActiva sesion) {
        return FormatoVisible.de(gestion.guardar(solicitud, sesion));
    }
}
