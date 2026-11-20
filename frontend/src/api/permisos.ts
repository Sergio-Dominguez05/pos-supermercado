import { pedir } from './cliente'

/** HU-01.10: un permiso y si lo tiene cada rol configurable; el Administrador siempre lo tiene. */
export interface PermisoDeRol {
  codigo: string
  descripcion: string
  /** Crit. 4: solo del Administrador; no se asigna a otros roles. */
  exclusivoAdmin: boolean
  supervisor: boolean
  cajero: boolean
}

export interface MatrizPermisos {
  modulos: { codigo: string; nombre: string; permisos: PermisoDeRol[] }[]
  rolesConfigurables: ('SUPERVISOR' | 'CAJERO')[]
}

export type RolConfigurable = 'SUPERVISOR' | 'CAJERO'

export const CLAVE_PERMISOS = ['permisos'] as const

export const obtenerMatrizPermisos = () => pedir<MatrizPermisos>('/api/permisos')

/** Deja al rol con exactamente estos permisos; el servidor rechaza los exclusivos del Administrador. */
export const guardarPermisosDeRol = (rol: RolConfigurable, permisos: string[]) =>
  pedir<MatrizPermisos>(`/api/roles/${rol}/permisos`, { method: 'PUT', body: JSON.stringify({ permisos }) })
