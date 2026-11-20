package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** HU-10.07 crit. 3: las facturas certificadas del período, una por fila, en orden de emisión. */
public record DetalleFacturasIva(EncabezadoReporte encabezado, List<Factura> facturas) {

    public static final String TITULO = "Detalle de IVA por factura";

    public record Factura(long facturaId, OffsetDateTime emitidaEn, String serie, String numero, String autorizacion,
                          String receptorId, String receptorNombre, String caja, BigDecimal baseImponible,
                          BigDecimal iva, BigDecimal total) {}
}
