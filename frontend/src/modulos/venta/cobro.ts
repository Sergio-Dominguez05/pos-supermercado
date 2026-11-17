import type { Cobro, FacturaDeVenta, Pago, VentaFinalizada } from '../../api/pagos'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-07.03 crit. 4: el campo de los últimos 4 dígitos solo admite dígitos, y como mucho 4. */
export const soloUltimos4 = (texto: string) => texto.replace(/\D/g, '').slice(0, 4)

/** Crit. 7: un monto en quetzales, mayor que cero y con dos decimales como mucho. */
export const montoValido = (texto: string) => /^\d+(\.\d{1,2})?$/.test(texto.trim()) && Number(texto) > 0

/** Cómo se muestra un pago registrado: tipo, últimos 4 dígitos si los hay, autorización y monto. */
export const descripcionPago = (pago: Pago) =>
  `Tarjeta de ${pago.tipoTarjeta === 'DEBITO' ? 'débito' : 'crédito'}` +
  `${pago.ultimos4 ? ` ••••${pago.ultimos4}` : ''} · Aut. ${pago.autorizacion} · ${formatoQuetzales(pago.monto)}`

/** HU-07.06: se confirma antes de eliminar un pago, y se recuerda anularlo también en la terminal bancaria. */
export const confirmacionEliminarPago = (pago: Pago) =>
  `¿Eliminar el pago (${descripcionPago(pago)})? Si la terminal ya lo cobró, anúlelo también en la terminal bancaria.`

/**
 * HU-07.07: por qué todavía no se puede finalizar el cobro, o `undefined` si los pagos cubren el total (crit. 1 y 2).
 */
export const motivoSinFinalizar = (cobro: Cobro) =>
  cobro.completo
    ? undefined
    : `Para finalizar, los pagos deben cubrir el total: falta cobrar ${formatoQuetzales(cobro.pendiente)}.`

/**
 * HU-08.03: cómo quedó la factura. Certificada, con su serie y número (crit. 2); si no, el motivo que da el servidor:
 * en contingencia se certificará después (crit. 3) y el rechazo se corrige con HU-08.06.
 */
export const avisoFactura = (factura: FacturaDeVenta) => {
  if (factura.estado === 'CERTIFICADA') return `Factura certificada: serie ${factura.serie}, N.º ${factura.numero}.`
  return factura.motivo ?? 'La factura está pendiente de certificar.'
}

/** HU-07.08: qué venta se finalizó, cuánto se cobró y cómo quedó su factura; la pantalla ya está en la siguiente. */
export const avisoVentaFinalizada = (venta: VentaFinalizada) =>
  `Venta N.º ${venta.numero} finalizada: ${venta.articulos} ${venta.articulos === 1 ? 'artículo' : 'artículos'}, ` +
  `${formatoQuetzales(venta.pagado)} cobrados. ${avisoFactura(venta.factura)}`
