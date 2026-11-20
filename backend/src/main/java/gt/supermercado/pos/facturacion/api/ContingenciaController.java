package gt.supermercado.pos.facturacion.api;

import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias;
import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias.Resumen;
import gt.supermercado.pos.facturacion.aplicacion.CertificacionContingencias.Ronda;
import gt.supermercado.pos.facturacion.dominio.FacturaPendiente;
import gt.supermercado.pos.facturacion.dominio.PlazoContingencia;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** HU-08.07 crit. 3, HU-08.08 crit. 4 y HU-08.09: el estado de la contingencia y la certificación de pendientes. */
@RestController
public class ContingenciaController {

    private final CertificacionContingencias contingencias;

    public ContingenciaController(CertificacionContingencias contingencias) {
        this.contingencias = contingencias;
    }

    /** HU-08.07 crit. 3: si hay comprobantes en contingencia, para el aviso de la pantalla de venta. */
    public record EstadoContingencia(boolean enContingencia, int pendientes) {}

    public record Pendiente(long ventaId, long ventaNumero, String cajaNombre, String estado, Long numeroAcceso,
                            OffsetDateTime emitidaEn, BigDecimal total, int intentos, String ultimoError,
                            OffsetDateTime proximoIntento, long dias, LocalDate vence, boolean alerta) {

        static Pendiente de(FacturaPendiente pendiente, LocalDate hoy) {
            return new Pendiente(pendiente.ventaId(), pendiente.ventaNumero(), pendiente.cajaNombre(),
                    pendiente.estado().name(), pendiente.numeroAcceso(), pendiente.emitidaEn(), pendiente.total(),
                    pendiente.intentos(), pendiente.ultimoError(), pendiente.proximoIntento(),
                    PlazoContingencia.dias(pendiente.emitidaEn(), hoy), PlazoContingencia.vence(pendiente.emitidaEn()),
                    PlazoContingencia.enAlerta(pendiente.emitidaEn(), hoy));
        }
    }

    /** HU-08.09 crit. 1 y 2: cuántas, la más antigua, si hay alerta, y crit. 3: la lista con el último error. */
    public record RespuestaContingencia(int pendientes, OffsetDateTime masAntigua, long diasMasAntigua,
                                        LocalDate venceMasAntigua, boolean alerta, int diasMaximos, int diaDeAlerta,
                                        List<Pendiente> lista) {}

    public record RespuestaRonda(int procesadas, int certificadas, int rechazadas, int quedan, boolean ocupado,
                                 String mensaje) {

        static RespuestaRonda de(Ronda ronda) {
            return new RespuestaRonda(ronda.procesadas(), ronda.certificadas(), ronda.rechazadas(), ronda.quedan(),
                    ronda.ocupado(), ronda.mensaje());
        }
    }

    @GetMapping("/api/facturas/contingencia/estado")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
    public EstadoContingencia estado() {
        int pendientes = contingencias.resumen().pendientes();
        return new EstadoContingencia(pendientes > 0, pendientes);
    }

    @GetMapping("/api/facturas/contingencia")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public RespuestaContingencia resumen() {
        Resumen resumen = contingencias.resumen();
        LocalDate hoy = PlazoContingencia.hoy();
        return new RespuestaContingencia(resumen.pendientes(), resumen.masAntigua(), resumen.diasMasAntigua(),
                resumen.venceMasAntigua(), resumen.alerta(), PlazoContingencia.DIAS_MAXIMOS,
                PlazoContingencia.DIA_DE_ALERTA, resumen.lista().stream().map(p -> Pendiente.de(p, hoy)).toList());
    }

    /** HU-08.08 crit. 4: certificar ahora, sin esperar el proceso automático. */
    @PostMapping("/api/facturas/contingencia/certificar")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public RespuestaRonda certificar(@AuthenticationPrincipal SesionActiva sesion) {
        return RespuestaRonda.de(contingencias.certificarAhora(sesion));
    }
}
