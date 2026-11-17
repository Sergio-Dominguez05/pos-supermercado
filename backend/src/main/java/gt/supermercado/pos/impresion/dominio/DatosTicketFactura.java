package gt.supermercado.pos.impresion.dominio;

import gt.supermercado.pos.configuracion.dominio.Emisor;
import gt.supermercado.pos.configuracion.dominio.Establecimiento;
import gt.supermercado.pos.pagos.dominio.Pago;
import gt.supermercado.pos.ventas.dominio.Venta;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lo que va en la factura impresa (HU-08.05 crit. 1 y 3). El emisor y el establecimiento son la copia guardada en la
 * factura al emitirla, no la configuración de hoy.
 *
 * @param certificador    el nombre del certificador FEL
 * @param nitCertificador su NIT, o {@code null} si todavía no se configuró
 */
public record DatosTicketFactura(Emisor emisor, Establecimiento establecimiento, String receptorId,
                                 String receptorNombre, UUID autorizacion, String serie, String numero,
                                 OffsetDateTime certificadaEn, OffsetDateTime emitidaEn, Venta venta, List<Pago> pagos,
                                 String certificador, String nitCertificador) {
}
