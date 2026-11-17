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
  /** Cuándo se dejó en espera (HU-06.12); `null` si nunca se suspendió. */
  suspendidaEn: string | null
  lineas: LineaVenta[]
  articulos: number
  subtotal: number
  iva: number
  total: number
}

export const CLAVE_VENTA_EN_CURSO = ['ventas', 'actual'] as const
export const CLAVE_VENTAS_SUSPENDIDAS = ['ventas', 'suspendidas'] as const

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

/**
 * HU-06.07: quita la línea de la venta en curso, sin autorización de un supervisor, y devuelve la venta con los
 * totales recalculados. La línea no se borra: queda marcada como eliminada y registrada en la bitácora.
 */
export const eliminarLinea = (lineaId: number) =>
  pedir<Venta>(`/api/ventas/actual/lineas/${lineaId}`, { method: 'DELETE' })

/**
 * HU-06.12: deja en espera la venta en curso, con todas sus líneas, y devuelve la venta suspendida. Después hay que
 * iniciar la siguiente venta. Responde 422 `VENTA_VACIA` si no tiene productos.
 */
export const suspenderVenta = () => pedir<Venta>('/api/ventas/actual/suspender', { method: 'POST' })

/** HU-06.13 crit. 1 y 3: las ventas en espera de la caja propia, de la más antigua a la más reciente. */
export const listarSuspendidas = () => pedir<Venta[]>('/api/ventas/suspendidas')

/**
 * HU-06.13 crit. 2: retoma la venta suspendida como venta en curso, con todas sus líneas y sus precios. Responde 404
 * `VENTA_SUSPENDIDA_NO_ENCONTRADA` si no está en espera en la caja propia y 422 `VENTA_EN_CURSO_CON_PRODUCTOS` si hay
 * otra venta en curso con productos.
 */
export const retomarVenta = (ventaId: number) =>
  pedir<Venta>(`/api/ventas/suspendidas/${ventaId}/retomar`, { method: 'POST' })
