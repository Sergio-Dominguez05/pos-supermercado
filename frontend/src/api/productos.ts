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

/** HU-03.01: registra un producto activo. Un precio vacío viaja como `null` para que el servidor lo rechace. */
export const crearProducto = ({ codigoBarras, descripcion, precio }: DatosProducto) =>
  pedir<Producto>('/api/productos', {
    method: 'POST',
    body: JSON.stringify({
      codigoBarras,
      descripcion,
      precio: precio.trim() === '' ? null : Number(precio.trim().replace(',', '.')),
    }),
  })

export const obtenerProducto = (id: number) => pedir<Producto>(`/api/productos/${id}`)
