import { pedir } from './cliente'

/** HU-12.05: caja registrada. Nunca trae el token del equipo, solo si hay uno vinculado. */
export interface Caja {
  id: number
  nombre: string
  /** Serie de facturación, o `null` si la caja no tiene una propia. */
  serieFel: string | null
  activo: boolean
  equipoVinculado: boolean
}

export interface DatosCaja {
  nombre: string
  serieFel: string
}

export const CLAVE_CAJAS = ['cajas'] as const
export const claveCaja = (id: number) => ['cajas', id] as const
export const CLAVE_CAJA_DEL_EQUIPO = ['cajas', 'equipo'] as const

export const listarCajas = () => pedir<Caja[]>('/api/cajas')

export const obtenerCaja = (id: number) => pedir<Caja>(`/api/cajas/${id}`)

export const crearCaja = (datos: DatosCaja) =>
  pedir<Caja>('/api/cajas', { method: 'POST', body: JSON.stringify(datos) })

export const editarCaja = (id: number, datos: DatosCaja) =>
  pedir<Caja>(`/api/cajas/${id}`, { method: 'PUT', body: JSON.stringify(datos) })

export const desactivarCaja = (id: number) => pedir<Caja>(`/api/cajas/${id}/desactivar`, { method: 'POST' })

export const activarCaja = (id: number) => pedir<Caja>(`/api/cajas/${id}/activar`, { method: 'POST' })

/**
 * Vincula este navegador a la caja (crit. 4). El servidor deja el token en una cookie HttpOnly que el frontend
 * no puede leer; desde entonces, quien inicie sesión aquí queda en esa caja.
 */
export const vincularEquipo = (id: number) => pedir<Caja>(`/api/cajas/${id}/equipo`, { method: 'POST' })

/** Caja a la que está vinculado este navegador, o `null` (el servidor responde 204). */
export const cajaDelEquipo = async () => (await pedir<Caja | undefined>('/api/cajas/equipo')) ?? null
