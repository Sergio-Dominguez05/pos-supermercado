import { ErrorDeApi, pedir } from './cliente'

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

/** Si el error es el de un código que no existe en el catálogo (HU-03.08). */
export const esCodigoNoEncontrado = (error: unknown) =>
  error instanceof ErrorDeApi && error.codigo === 'PRODUCTO_NO_ENCONTRADO'

/** HU-03.08 crit. 4: código buscado que no existe, pendiente de registrar. */
export interface CodigoNoEncontrado {
  codigo: string
  veces: number
  /** Búsqueda más reciente, en ISO 8601. */
  ultimaVez: string
  /** Caja de la búsqueda más reciente, o `null` si fue desde un equipo sin caja. */
  ultimaCaja: string | null
}

export const CLAVE_CODIGOS_NO_ENCONTRADOS = ['productos', 'no-encontrados'] as const

export const listarCodigosNoEncontrados = () => pedir<CodigoNoEncontrado[]>('/api/productos/codigos-no-encontrados')

/** Caracteres mínimos para buscar por descripción (HU-03.07 crit. 1); el servidor exige lo mismo. */
export const MINIMO_BUSQUEDA = 3

export const claveBusquedaDescripcion = (texto: string) => ['productos', 'busqueda', texto] as const

/** HU-03.07: productos activos cuya descripción contiene el texto, sin importar mayúsculas ni tildes. */
export const buscarPorDescripcion = (texto: string) =>
  pedir<Producto[]>(`/api/productos/busqueda?${new URLSearchParams({ q: texto.trim() })}`)

/** HU-03.06: búsqueda exacta por código; si no existe, el servidor responde 404 `PRODUCTO_NO_ENCONTRADO`. */
export const buscarPorCodigo = (codigo: string) =>
  pedir<Producto>(`/api/productos/codigo/${encodeURIComponent(codigo.trim())}`)
