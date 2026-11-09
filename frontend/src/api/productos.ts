import { pedir } from './cliente'

/** EP-03: producto del catálogo. El precio incluye el IVA. */
export interface Producto {
  id: number
  codigoBarras: string
  descripcion: string
  precio: number
  activo: boolean
  /** Última modificación (alta, edición o cambio de estado), en ISO 8601. */
  modificadoEn: string
}

export interface DatosProducto {
  codigoBarras: string
  descripcion: string
  /** Tal como lo escribió el usuario; el servidor valida que sea mayor que cero y con 2 decimales como máximo. */
  precio: string
}

/** HU-03.04: búsqueda, filtro, orden y página de la lista del catálogo. */
export interface FiltroProductos {
  q: string
  activo: 'true' | 'false' | ''
  orden: 'DESCRIPCION' | 'PRECIO' | 'MODIFICADO'
  descendente: boolean
  pagina: number
}

export const FILTRO_PRODUCTOS_INICIAL: FiltroProductos = {
  q: '',
  activo: '',
  orden: 'DESCRIPCION',
  descendente: false,
  pagina: 0,
}

export interface PaginaProductos {
  productos: Producto[]
  pagina: number
  tamano: number
  /** Productos que cumplen el filtro, en todas las páginas. */
  total: number
}

export const CLAVE_PRODUCTOS = ['productos'] as const
export const claveProducto = (id: number) => ['productos', id] as const
export const claveListaProductos = (filtro: FiltroProductos) => ['productos', 'lista', filtro] as const

/** HU-03.04: una página de la lista del catálogo. */
export function listarProductos(filtro: FiltroProductos) {
  const parametros = new URLSearchParams()
  if (filtro.q.trim()) parametros.set('q', filtro.q.trim())
  if (filtro.activo) parametros.set('activo', filtro.activo)
  parametros.set('orden', filtro.orden)
  if (filtro.descendente) parametros.set('descendente', 'true')
  if (filtro.pagina > 0) parametros.set('pagina', String(filtro.pagina))
  return pedir<PaginaProductos>(`/api/productos?${parametros}`)
}

/** Un precio vacío viaja como `null` para que el servidor lo rechace; se acepta la coma decimal. */
const precioParaEnviar = (precio: string) => (precio.trim() === '' ? null : Number(precio.trim().replace(',', '.')))

/** HU-03.01: registra un producto activo. */
export const crearProducto = ({ codigoBarras, descripcion, precio }: DatosProducto) =>
  pedir<Producto>('/api/productos', {
    method: 'POST',
    body: JSON.stringify({ codigoBarras, descripcion, precio: precioParaEnviar(precio) }),
  })

/** HU-03.02: cambia la descripción y el precio. El código no se envía porque no se puede modificar. */
export const editarProducto = (id: number, { descripcion, precio }: Omit<DatosProducto, 'codigoBarras'>) =>
  pedir<Producto>(`/api/productos/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ descripcion, precio: precioParaEnviar(precio) }),
  })

/** HU-03.03: los productos no se borran; se desactivan y se pueden volver a activar. */
export const desactivarProducto = (id: number) => pedir<Producto>(`/api/productos/${id}/desactivar`, { method: 'POST' })

export const activarProducto = (id: number) => pedir<Producto>(`/api/productos/${id}/activar`, { method: 'POST' })

export const obtenerProducto = (id: number) => pedir<Producto>(`/api/productos/${id}`)
