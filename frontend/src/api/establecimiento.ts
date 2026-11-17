import { pedir } from './cliente'

/** HU-12.02: el establecimiento tal como está inscrito en la SAT (en V1, el de la única sucursal). */
export interface Establecimiento {
  configurado: boolean
  codigoSat: string | null
  nombre: string | null
  direccion: string | null
  modificadoEn: string | null
}

export interface DatosEstablecimiento {
  codigoSat: string
  nombre: string
  direccion: string
}

export const CLAVE_ESTABLECIMIENTO = ['configuracion', 'establecimiento'] as const

export const obtenerEstablecimiento = () => pedir<Establecimiento>('/api/configuracion/establecimiento')

/** Guarda los datos. 422 si el código no es un número SAT válido o ya es de otra sucursal. */
export const guardarEstablecimiento = (datos: DatosEstablecimiento) =>
  pedir<Establecimiento>('/api/configuracion/establecimiento', { method: 'PUT', body: JSON.stringify(datos) })
