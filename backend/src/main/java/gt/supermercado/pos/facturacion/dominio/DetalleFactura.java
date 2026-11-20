package gt.supermercado.pos.facturacion.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * HU-08.14 Consultar el detalle de una factura.
 * <ul>
 *   <li>Crit. 1: el emisor y el establecimiento (la copia guardada al emitir, no la configuración de hoy), el
 *       receptor, las líneas vigentes, el Subtotal, el IVA y el TOTAL de la venta, los pagos vigentes y los datos de
 *       certificación.</li>
 *   <li>Crit. 2: el historial de la factura en orden: emisión, intentos ante el certificador, certificación,
 *       reimpresiones y anulación. En V1 no hay notas de crédito, así que no aparecen.</li>
 * </ul>
 * Las acciones del crit. 3 (reimprimir y anular) usan los endpoints que ya existen; el envío por correo llega con
 * HU-08.15. {@code turnoAbierto} dice si todavía se puede anular (HU-08.10, decisión D2: con el turno de la venta
 * abierto).
 */
public record DetalleFactura(long facturaId, long ventaId, long ventaNumero, EstadoFactura estado, String caja,
                             String cajero, boolean turnoAbierto, OffsetDateTime emitidaEn, Emisor emisor,
                             Receptor receptor, Certificacion certificacion, List<Linea> lineas, BigDecimal subtotal,
                             BigDecimal iva, BigDecimal total, List<Pago> pagos, List<Evento> historial) {

    public record Emisor(String nombreComercial, String razonSocial, String nit, String direccionFiscal,
                         String establecimiento, String codigoEstablecimiento, String direccionEstablecimiento) {}

    public record Receptor(String tipo, String id, String nombre, String direccion, String correo) {}

    /** {@code null} en lo que todavía no existe (por ejemplo, la autorización de una factura en contingencia). */
    public record Certificacion(String serie, String numero, UUID autorizacion, OffsetDateTime certificadaEn,
                                Long numeroAcceso) {}

    public record Linea(String descripcion, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {}

    /** @param ultimos4 los últimos cuatro dígitos, si se registraron; nunca el número completo */
    public record Pago(String tipoTarjeta, String autorizacion, String ultimos4, BigDecimal monto) {}

    /**
     * Un hecho del historial (crit. 2).
     *
     * @param usuario quién lo hizo, cuando se sabe (reimpresión y anulación)
     */
    public record Evento(OffsetDateTime momento, TipoEvento tipo, String descripcion, String usuario) {}

    public enum TipoEvento { EMITIDA, CERTIFICADA, RECHAZADA, SIN_RESPUESTA, CONSULTA, REIMPRESA, ANULADA, ANULACION_RECHAZADA }
}
