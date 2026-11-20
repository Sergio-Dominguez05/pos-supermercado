package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.ReenvioFacturas;
import gt.supermercado.pos.facturacion.dominio.FacturaRechazada;
import gt.supermercado.pos.facturacion.dominio.MotivoRechazo;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-08.06 crit. 3: las ventas pendientes de facturar porque el certificador rechazó su factura, para el supervisor
 * (quien tiene el permiso de gestionar facturas: Supervisor y Administrador).
 */
@RestController
@RequestMapping("/api/facturas/rechazadas")
public class FacturasRechazadasController {

    private final ReenvioFacturas reenvio;

    public FacturasRechazadasController(ReenvioFacturas reenvio) {
        this.reenvio = reenvio;
    }

    /** Cada venta con el motivo comprensible (crit. 1) y la respuesta completa del certificador (crit. 4). */
    public record RespuestaRechazada(long ventaId, long ventaNumero, String cajaNombre, String cajero,
                                     OffsetDateTime emitidaEn, BigDecimal total, String receptorId,
                                     String receptorNombre, MotivoRechazo.Tipo tipo, String motivo,
                                     boolean corregibleEnCaja, String codigo, String mensajeCertificador,
                                     String respuesta, OffsetDateTime rechazadaEn) {

        static RespuestaRechazada de(FacturaRechazada rechazada) {
            MotivoRechazo motivo = rechazada.motivo();
            return new RespuestaRechazada(rechazada.ventaId(), rechazada.ventaNumero(), rechazada.cajaNombre(),
                    rechazada.cajero(), rechazada.emitidaEn(), rechazada.total(), rechazada.receptorId(),
                    rechazada.receptorNombre(), motivo.tipo(), motivo.mensaje(), motivo.corregibleEnCaja(),
                    rechazada.codigo(), rechazada.mensajeCertificador(), rechazada.respuesta(), rechazada.rechazadaEn());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public List<RespuestaRechazada> listar() {
        return reenvio.rechazadas().stream().map(RespuestaRechazada::de).toList();
    }

    /** Reenvía la factura con el mismo receptor, cuando ya se corrigió la causa del rechazo. */
    @PostMapping("/{ventaId}/reenviar")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public FacturaDeVenta reenviar(@PathVariable long ventaId, @AuthenticationPrincipal SesionActiva sesion) {
        return reenvio.reenviar(ventaId, sesion);
    }
}
