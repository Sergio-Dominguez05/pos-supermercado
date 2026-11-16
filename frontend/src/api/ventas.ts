import { pedir } from './cliente'

/** Una línea de la venta: copia de la descripción y del precio al agregarla (HU-06.09), con su desglose de IVA. */
export interface LineaVenta {
  id: number
  productoId: number
  codigoBarras: string
  descripcion: string
  cantidad: number
  precioUnitario: number
  subtotalConIva: number
  baseImponible: number
  iva: number
}

/** La venta en curso. Los totales los calcula el servidor; la pantalla solo los muestra (Architecture.md §6.1). */
export interface Venta {
  id: number
  /** Número interno único (HU-06.01 crit. 4). */
  numero: number
  estado: 'EN_CURSO' | 'SUSPENDIDA' | 'CANCELADA' | 'FINALIZADA'
  cajaNombre: string
  creadaEn: string
  lineas: LineaVenta[]
  articulos: number
  subtotal: number
  iva: number
  total: number
}

export const CLAVE_VENTA_EN_CURSO = ['ventas', 'actual'] as const

/** HU-06.01: inicia la venta del turno, o devuelve la que ya está en curso. */
export const iniciarVenta = () => pedir<Venta>('/api/ventas', { method: 'POST' })

/** HU-06.14: cancela la venta en curso. Sin autorización de un supervisor. */
export const cancelarVenta = () => pedir<Venta>('/api/ventas/actual/cancelar', { method: 'POST' })
