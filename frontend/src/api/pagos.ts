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
  /** HU-07.07: los pagos cubren el total, así que se puede finalizar. */
  completo: boolean
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

/**
 * HU-07.06: elimina un pago de la venta en curso y devuelve el cobro con el pendiente recalculado. El pago no se
 * borra: queda marcado como eliminado y registrado en la bitácora. Responde 404 `PAGO_NO_ENCONTRADO` si no es un pago
 * vigente de la venta en curso.
 */
export const eliminarPago = (pagoId: number) =>
  pedir<Cobro>(`/api/ventas/actual/pagos/${pagoId}`, { method: 'DELETE' })

/** HU-07.08: la venta finalizada, con lo cobrado y si se emitió la factura (EP-08). */
/** HU-08.03: cómo quedó la factura de la venta al finalizar el cobro. */
export interface FacturaDeVenta {
  estado: 'CERTIFICADA' | 'CONTINGENCIA' | 'RECHAZADA' | 'PENDIENTE'
  /** El número de autorización (UUID) de la SAT, si se certificó. */
  autorizacion: string | null
  serie: string | null
  numero: string | null
  certificadaEn: string | null
  /** Por qué no se certificó, si no se certificó, en lenguaje comprensible (HU-08.06 crit. 1). */
  motivo: string | null
  /** HU-08.06 crit. 2: el rechazo se corrige en caja (el cliente) y se reenvía sin volver a cobrar. */
  corregible: boolean
}

/** HU-08.05: si la factura se imprimió; `mensaje` dice por qué no, si hay algo que decir. */
export interface ResultadoImpresion {
  impresa: boolean
  mensaje: string | null
}

export interface VentaFinalizada {
  ventaId: number
  numero: number
  articulos: number
  total: number
  pagado: number
  pagos: Pago[]
  factura: FacturaDeVenta
  impresion: ResultadoImpresion
}

/**
 * HU-07.08: finaliza el cobro de la venta en curso: queda registrada con sus pagos y se certifica la factura
 * (HU-08.03). Responde 422 `PAGO_INCOMPLETO` si los pagos no cubren el total, o `EMISOR_NO_CONFIGURADO` y similares si
 * falta configurar los datos de la factura. Si falla, la venta sigue en curso y se puede reintentar.
 */
export const finalizarCobro = () => pedir<VentaFinalizada>('/api/ventas/actual/finalizar', { method: 'POST' })
