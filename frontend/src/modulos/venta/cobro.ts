import type { Pago } from '../../api/pagos'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-07.03 crit. 4: el campo de los últimos 4 dígitos solo admite dígitos, y como mucho 4. */
export const soloUltimos4 = (texto: string) => texto.replace(/\D/g, '').slice(0, 4)

/** Crit. 7: un monto en quetzales, mayor que cero y con dos decimales como mucho. */
export const montoValido = (texto: string) => /^\d+(\.\d{1,2})?$/.test(texto.trim()) && Number(texto) > 0

/** Cómo se muestra un pago registrado: tipo, últimos 4 dígitos si los hay, autorización y monto. */
export const descripcionPago = (pago: Pago) =>
  `Tarjeta de ${pago.tipoTarjeta === 'DEBITO' ? 'débito' : 'crédito'}` +
  `${pago.ultimos4 ? ` ••••${pago.ultimos4}` : ''} · Aut. ${pago.autorizacion} · ${formatoQuetzales(pago.monto)}`
