package gt.supermercado.pos.facturacion.aplicacion;

import gt.supermercado.pos.facturacion.dominio.FacturaPendiente;
import gt.supermercado.pos.facturacion.dominio.PlazoContingencia;
import gt.supermercado.pos.facturacion.dominio.RepositorioFacturas;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.seguridad.dominio.SesionActiva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HU-08.08 Certificar los comprobantes en contingencia y HU-08.09 Avisar las contingencias pendientes.
 * <ul>
 *   <li>08.08 crit. 1: un proceso automático (cada minuto) envía las pendientes en orden de emisión, cuando ya
 *       cumplieron su espera. Cada una se consulta primero por el identificador de la venta (HU-08.04).</li>
 *   <li>08.08 crit. 2: certificada, guarda su autorización en la factura de la venta.</li>
 *   <li>08.08 crit. 3: si el certificador rechaza una, queda rechazada (HU-08.06) y se sigue con las demás. Si deja de
 *       responder, la ronda se detiene: las demás esperarían lo mismo, y siguen en orden en la próxima.</li>
 *   <li>08.08 crit. 4: el supervisor la lanza a mano, sin esperar el siguiente intento de cada una.</li>
 *   <li>08.09: cuántas hay, la más antigua y una alerta desde el día 12 de los 15 que da la SAT.</li>
 * </ul>
 * Solo corre una ronda a la vez.
 */
@Service
public class CertificacionContingencias {

    static final int LOTE = 50;

    private static final Logger LOG = LoggerFactory.getLogger(CertificacionContingencias.class);

    private final RepositorioFacturas facturas;
    private final EmisionFacturas emision;
    private final ReentrantLock enCurso = new ReentrantLock();

    public CertificacionContingencias(RepositorioFacturas facturas, EmisionFacturas emision) {
        this.facturas = facturas;
        this.emision = emision;
    }

    /** Cómo terminó una ronda. {@code ocupado}: ya había otra en curso y esta no hizo nada. */
    public record Ronda(int procesadas, int certificadas, int rechazadas, int sinRespuesta, int quedan,
                        boolean ocupado) {

        public String mensaje() {
            if (ocupado) {
                return "Ya se están certificando los comprobantes pendientes: espere un momento.";
            }
            if (procesadas == 0) {
                return "No hay comprobantes pendientes de certificar.";
            }
            return ("Se certificaron %d de %d comprobantes; %d rechazados%s. Quedan %d pendientes.")
                    .formatted(certificadas, procesadas, rechazadas,
                            sinRespuesta > 0 ? "; el certificador dejó de responder" : "", quedan);
        }
    }

    /** El resumen de HU-08.09 crit. 1 y 2. {@code masAntigua} es {@code null} si no hay pendientes. */
    public record Resumen(int pendientes, OffsetDateTime masAntigua, long diasMasAntigua, LocalDate venceMasAntigua,
                          boolean alerta, List<FacturaPendiente> lista) {}

    /** El proceso automático: solo las que ya cumplieron su espera, a nombre del usuario técnico "sistema". */
    public Ronda certificarPendientes(SesionActiva sistema) {
        return ronda(false, sistema);
    }

    /** Crit. 4: el supervisor la lanza a mano, también con las que todavía esperan. */
    public Ronda certificarAhora(SesionActiva sesion) {
        return ronda(true, sesion);
    }

    private Ronda ronda(boolean todas, SesionActiva sesion) {
        if (!enCurso.tryLock()) {
            return new Ronda(0, 0, 0, 0, facturas.pendientes().size(), true);
        }
        try {
            int procesadas = 0;
            int certificadas = 0;
            int rechazadas = 0;
            int sinRespuesta = 0;
            for (FacturaPendiente pendiente : facturas.porCertificar(todas, LOTE)) {
                procesadas++;
                FacturaDeVenta resultado;
                try {
                    resultado = emision.reintentar(pendiente.ventaId(), sesion);
                } catch (RuntimeException e) {
                    // Una falla con una factura no detiene a las demás (crit. 3).
                    LOG.warn("No se pudo certificar la factura {} en contingencia", pendiente.facturaId(), e);
                    continue;
                }
                switch (resultado.estado()) {
                    case "CERTIFICADA" -> certificadas++;
                    case "RECHAZADA" -> rechazadas++;
                    default -> sinRespuesta++;
                }
                if (sinRespuesta > 0) {
                    break;
                }
            }
            return new Ronda(procesadas, certificadas, rechazadas, sinRespuesta, facturas.pendientes().size(), false);
        } finally {
            enCurso.unlock();
        }
    }

    @Transactional(readOnly = true)
    public Resumen resumen() {
        List<FacturaPendiente> lista = facturas.pendientes();
        if (lista.isEmpty()) {
            return new Resumen(0, null, 0, null, false, lista);
        }
        LocalDate hoy = PlazoContingencia.hoy();
        OffsetDateTime masAntigua = lista.getFirst().emitidaEn();
        return new Resumen(lista.size(), masAntigua, PlazoContingencia.dias(masAntigua, hoy),
                PlazoContingencia.vence(masAntigua), PlazoContingencia.enAlerta(masAntigua, hoy), lista);
    }
}
