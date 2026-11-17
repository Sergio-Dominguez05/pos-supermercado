import { pedir } from './cliente'

export type TipoTarjeta = 'DEBITO' | 'CREDITO'

/** Un pago con tarjeta procesado en la terminal bancaria. Nunca incluye el número completo de la tarjeta. */
export interface Pago {
  id: number
  tipoTarjeta: TipoTarjeta
  autorizacion: string
  /** Los últimos 4 dígitos, si el cajero los registró (HU-07.03 crit. 4). */
  ultimos4: string | null
  monto: number
  registradoEn: string
}

/** HU-07.03 crit. 1 y 2: el total a cobrar de la venta en curso, lo pagado y lo pendiente. */
export interface Cobro {
  ventaId: number
  numero: number
  total: number
  pagado: number
  pendiente: number
  pagos: Pago[]
}

export interface SolicitudPago {
  tipoTarjeta?: TipoTarjeta
  autorizacion: string
  ultimos4?: string
  monto: number
}

export const CLAVE_COBRO = ['ventas', 'actual', 'cobro'] as const

export const obtenerCobro = () => pedir<Cobro>('/api/ventas/actual/pagos')

/**
 * HU-07.03: registra el pago con tarjeta aprobado por la terminal bancaria. Responde 422 con el motivo si falta el
 * tipo o la autorización, si los últimos 4 dígitos no son 4 dígitos, si el monto supera el pendiente
 * (`MONTO_MAYOR_AL_PENDIENTE`) o si la venta ya tiene su pago (`PAGO_YA_REGISTRADO`).
 */
export const registrarPago = (solicitud: SolicitudPago) =>
  pedir<Cobro>('/api/ventas/actual/pagos', { method: 'POST', body: JSON.stringify(solicitud) })
