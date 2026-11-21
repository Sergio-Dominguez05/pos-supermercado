package gt.supermercado.pos.impresion.api;

import gt.supermercado.pos.configuracion.aplicacion.GestionEmisor;
import gt.supermercado.pos.configuracion.aplicacion.GestionEstablecimiento;
import gt.supermercado.pos.configuracion.aplicacion.GestionFormatoComprobante;
import gt.supermercado.pos.configuracion.aplicacion.GestionFormatoComprobante.SolicitudFormato;
import gt.supermercado.pos.configuracion.aplicacion.LogoMonocromo;
import gt.supermercado.pos.configuracion.dominio.FormatoComprobante;
import gt.supermercado.pos.impresion.dominio.Ticket;
import gt.supermercado.pos.impresion.dominio.TicketFactura;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-12.09 crit. 3: la vista previa del ticket con el formato que se está editando, antes de guardarlo. Es una factura
 * de ejemplo con el emisor y el establecimiento configurados; no se guarda nada ni queda en la bitácora.
 */
@RestController
public class VistaPreviaComprobanteController {

    private final GestionFormatoComprobante formatos;
    private final GestionEmisor emisor;
    private final GestionEstablecimiento establecimientos;

    public VistaPreviaComprobanteController(GestionFormatoComprobante formatos, GestionEmisor emisor,
                                            GestionEstablecimiento establecimientos) {
        this.formatos = formatos;
        this.emisor = emisor;
        this.establecimientos = establecimientos;
    }

    /** El ticket renglón por renglón, con el logo en blanco y negro tal como saldría impreso. */
    public record VistaPrevia(int columnas, String logo, List<Ticket.Renglon> renglones) {}

    @PostMapping("/api/configuracion/comprobante/vista-previa")
    @PreAuthorize("hasAuthority('CONFIGURACION_MODIFICAR')")
    public VistaPrevia vistaPrevia(@RequestBody SolicitudFormato solicitud,
                                   @RequestParam(defaultValue = "80") int anchoMm) {
        FormatoComprobante formato = formatos.validar(solicitud);
        Ticket ticket = TicketFactura.ejemplo(emisor.emisor().orElse(null),
                establecimientos.deLaSucursal(establecimientos.sucursalPrincipal()).orElse(null),
                anchoMm == 58 ? 58 : 80, formato);
        return new VistaPrevia(ticket.columnas(), LogoMonocromo.dataUrl(formato.logo()), ticket.renglones());
    }
}
