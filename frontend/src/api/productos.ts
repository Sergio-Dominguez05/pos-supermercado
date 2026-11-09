import { pedir } from './cliente'

/** EP-03: producto del catálogo. El precio incluye el IVA. */
export interface Producto {
  id: number
  codigoBarras: string
  descripcion: string
  precio: number
  activo: boolean
}

export interface DatosProducto {
  codigoBarras: string
  descripcion: string
  /** Tal como lo escribió el usuario; el servidor valida que sea mayor que cero y con 2 decimales como máximo. */
  precio: string
}

export const CLAVE_PRODUCTOS = ['productos'] as const
export const claveProducto = (id: number) => ['productos', id] as const

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

export const obtenerProducto =(id: number) => pedir<Producto>(`/api/productos/${id}`)
