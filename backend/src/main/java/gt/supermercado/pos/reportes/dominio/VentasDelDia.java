package gt.supermercado.pos.reportes.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Las ventas finalizadas de un día (hora de Guatemala), sin las de facturas anuladas. */
public record VentasDelDia(LocalDate fecha, long cantidad, BigDecimal subtotal, BigDecimal iva, BigDecimal total) {}
