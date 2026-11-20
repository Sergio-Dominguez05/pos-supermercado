package gt.supermercado.pos.caja.dominio;

import java.math.BigDecimal;

/**
 * HU-09.08 y HU-09.10: lo que se cobró en un turno. Solo cuentan las ventas finalizadas; las facturas anuladas se
 * descuentan del total registrado porque su cobro se revierte en la terminal (decisión de Sergio) y se informan aparte.
 *
 * @param totalRegistrado lo cobrado con tarjeta que debe coincidir con el cierre de lote: débito + crédito
 * @param contingencias   comprobantes del turno todavía sin certificar (HU-08.07): su plazo sigue corriendo
 */
public record ResumenDeTurno(int ventas, BigDecimal debito, BigDecimal credito, int anuladas,
                             BigDecimal totalAnulado, BigDecimal totalRegistrado, int contingencias) {
}
