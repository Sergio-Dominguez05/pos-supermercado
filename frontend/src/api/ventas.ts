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

/**
 * HU-06.02: agrega a la venta en curso el producto con ese código; si ya estaba, suma a su línea. Responde 404
 * `PRODUCTO_NO_ENCONTRADO` (HU-03.08) o 422 `PRODUCTO_INACTIVO` (HU-03.09) sin agregar nada.
 *
 * HU-06.04: sin `cantidad` se agrega una unidad. Una cantidad que no es un entero entre 1 y 9 999 responde 422
 * `CANTIDAD_INVALIDA`; una mayor que el límite configurado responde 422 `CANTIDAD_POR_CONFIRMAR` hasta reenviarla con
 * `confirmada` (crit. 4).
 */
export const agregarPorCodigo = (codigo: string, cantidad?: number, confirmada?: boolean) =>
  pedir<Venta>('/api/ventas/actual/lineas', {
    method: 'POST',
    body: JSON.stringify({ codigo, cantidad, confirmada: confirmada || undefined }),
  })

/** HU-06.03 crit. 2: agrega el producto elegido en la búsqueda por descripción (o suma a su línea). */
export const agregarProducto = (productoId: number, cantidad?: number, confirmada?: boolean) =>
  pedir<Venta>('/api/ventas/actual/lineas', {
    method: 'POST',
    body: JSON.stringify({ productoId, cantidad, confirmada: confirmada || undefined }),
  })

/** HU-06.04 crit. 2: cambia la cantidad de una línea de la venta en curso, con las mismas reglas que al agregar. */
export const cambiarCantidad = (lineaId: number, cantidad: number, confirmada?: boolean) =>
  pedir<Venta>(`/api/ventas/actual/lineas/${lineaId}/cantidad`, {
    method: 'PUT',
    body: JSON.stringify({ cantidad, confirmada: confirmada || undefined }),
  })

/** HU-06.14: cancela la venta en curso. Sin autorización de un supervisor. */
export const cancelarVenta = () => pedir<Venta>('/api/ventas/actual/cancelar', { method: 'POST' })
