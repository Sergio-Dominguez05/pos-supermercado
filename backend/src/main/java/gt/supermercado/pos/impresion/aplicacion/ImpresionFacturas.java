package gt.supermercado.pos.impresion.aplicacion;

import gt.supermercado.pos.configuracion.aplicacion.GestionImpresoras;
import gt.supermercado.pos.configuracion.dominio.Impresora;
import gt.supermercado.pos.facturacion.aplicacion.ConsultaFacturas;
import gt.supermercado.pos.facturacion.aplicacion.ConsultaFacturas.Certificador;
import gt.supermercado.pos.facturacion.dominio.FacturaImprimible;
import gt.supermercado.pos.impresion.dominio.CanalImpresion;
import gt.supermercado.pos.impresion.dominio.DatosTicketFactura;
import gt.supermercado.pos.impresion.dominio.EscPos;
import gt.supermercado.pos.impresion.dominio.ImpresoraNoDisponibleException;
import gt.supermercado.pos.impresion.dominio.Ticket;
import gt.supermercado.pos.impresion.dominio.TicketFactura;
import gt.supermercado.pos.pagos.dominio.FacturaDeVenta;
import gt.supermercado.pos.pagos.dominio.ImpresionDeFactura;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.pagos.dominio.ResultadoImpresion;
import gt.supermercado.pos.ventas.dominio.Venta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * HU-08.05 Imprimir la factura: al finalizar el cobro, la factura certificada sale en la impresora de la caja
 * (HU-12.08), con las copias configuradas.
 * <ul>
 *   <li>Crit. 1 a 3: el contenido lo arma {@link TicketFactura} con la copia del emisor guardada en la factura.</li>
 *   <li>Crit. 6: si la impresora falla o la caja no tiene impresora, la venta y su factura siguen siendo válidas; se
 *       avisa al cajero que puede reimprimirla (HU-08.12).</li>
 * </ul>
 * Una factura en contingencia o rechazada no se imprime aquí: el comprobante de contingencia es de HU-08.07.
 */
@Service
public class ImpresionFacturas implements ImpresionDeFactura {

    static final String REIMPRIMIR = " La factura es válida: se puede reimprimir.";

    private static final Logger LOG = LoggerFactory.getLogger(ImpresionFacturas.class);

    private final ConsultaFacturas facturas;
    private final GestionImpresoras impresoras;
    private final CanalImpresion canal;

    public ImpresionFacturas(ConsultaFacturas facturas, GestionImpresoras impresoras, CanalImpresion canal) {
        this.facturas = facturas;
        this.impresoras = impresoras;
        this.canal = canal;
    }

    @Override
    public ResultadoImpresion imprimir(Venta venta, List<Pago> pagos, FacturaDeVenta factura) {
        if (!"CERTIFICADA".equals(factura.estado())) {
            return ResultadoImpresion.sinImprimir(null);
        }
        try {
            Optional<Impresora> impresora = impresoras.deLaCaja(venta.cajaId());
            if (impresora.isEmpty()) {
                return ResultadoImpresion.sinImprimir("La caja no tiene impresora configurada." + REIMPRIMIR);
            }
            enviar(venta, pagos, impresora.get(), impresora.get().copiasFactura(), false);
            return ResultadoImpresion.enviada();
        } catch (ImpresoraNoDisponibleException e) {
            return ResultadoImpresion.sinImprimir("No se pudo imprimir: " + e.getMessage() + REIMPRIMIR);
        } catch (RuntimeException e) {
            LOG.warn("Falló la impresión de la factura de la venta {}", venta.id(), e);
            return ResultadoImpresion.sinImprimir("No se pudo imprimir la factura." + REIMPRIMIR);
        }
    }

    /**
     * Arma el ticket de la factura de la venta y lo envía a la impresora. Lo usa también la reimpresión (HU-08.12), con
     * la leyenda de copia. Lanza {@link ImpresoraNoDisponibleException} si la impresora no responde.
     */
    public void enviar(Venta venta, List<Pago> pagos, Impresora impresora, int copias, boolean reimpresion) {
        FacturaImprimible imprimible = facturas.paraImprimir(venta.id()).orElseThrow();
        Ticket ticket = TicketFactura.armar(datos(imprimible, venta, pagos, facturas.certificador(), reimpresion),
                impresora.anchoMm());
        canal.enviar(impresora.host(), impresora.puerto(), EscPos.codificar(ticket, copias));
    }

    private static DatosTicketFactura datos(FacturaImprimible imprimible, Venta venta, List<Pago> pagos,
                                            Certificador certificador, boolean reimpresion) {
        return new DatosTicketFactura(imprimible.emisor(), imprimible.establecimiento(),
                imprimible.receptor().identificacion(), imprimible.receptor().nombre(),
                imprimible.factura().autorizacion(), imprimible.factura().serie(), imprimible.factura().numero(),
                imprimible.factura().fechaCertificacion(), imprimible.factura().emitidaEn(), venta, pagos,
                certificador.nombre(), certificador.nit(), reimpresion);
    }
}
