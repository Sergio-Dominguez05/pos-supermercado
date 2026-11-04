import type { UsuarioSesion } from './autenticacion'
import { pedir } from './cliente'

export type Rol = UsuarioSesion['rol']

export interface NuevoUsuario {
  nombreCompleto: string
  username: string
  rol: Rol
  /** Contraseña temporal (Administrador, Supervisor) o PIN temporal (Cajero). */
  credencialTemporal: string
}

export interface UsuarioCreado {
  id: number
  username: string
  nombreCompleto: string
  rol: Rol
  activo: boolean
  credencialTemporal: boolean
}

/** Fila de la lista de usuarios (HU-01.06). Nunca trae la credencial. */
export interface ResumenUsuario {
  id: number
  username: string
  nombreCompleto: string
  rol: Rol
  rolNombre: string
  activo: boolean
  /** ISO 8601, o `null` si nunca ha iniciado sesión. */
  ultimoAcceso: string | null
}

/** Cadena vacía = sin filtro. */
export interface FiltroUsuarios {
  q: string
  rol: Rol | ''
  activo: 'true' | 'false' | ''
}

export const FILTRO_INICIAL: FiltroUsuarios = { q: '', rol: '', activo: '' }

export const claveUsuarios = (filtro: FiltroUsuarios) => ['usuarios', filtro] as const

export function listarUsuarios(filtro: FiltroUsuarios) {
  const parametros = new URLSearchParams()
  if (filtro.q.trim()) parametros.set('q', filtro.q.trim())
  if (filtro.rol) parametros.set('rol', filtro.rol)
  if (filtro.activo) parametros.set('activo', filtro.activo)
  const consulta = parametros.toString()
  return pedir<ResumenUsuario[]>(`/api/usuarios${consulta ? `?${consulta}` : ''}`)
}

export const claveUsuario = (id: number) => ['usuarios', id] as const

export const obtenerUsuario = (id: number) => pedir<ResumenUsuario>(`/api/usuarios/${id}`)

/** Sin nombre de usuario: no se modifica después de creado (HU-01.07 crit. 2). */
export interface CambiosUsuario {
  nombreCompleto: string
  rol: Rol
  /** Solo al pasar entre Cajero y Administrador o Supervisor (crit. 3). */
  credencialTemporal?: string
}

export const editarUsuario = (id: number, cambios: CambiosUsuario) =>
  pedir<ResumenUsuario>(`/api/usuarios/${id}`, { method: 'PUT', body: JSON.stringify(cambios) })

/** HU-01.08: el usuario desactivado no puede iniciar sesión; nunca se borra. */
export const desactivarUsuario = (id: number) =>
  pedir<ResumenUsuario>(`/api/usuarios/${id}/desactivar`, { method: 'POST' })

export const activarUsuario = (id: number) => pedir<ResumenUsuario>(`/api/usuarios/${id}/activar`, { method: 'POST' })

export const crearUsuario = (datos: NuevoUsuario) =>
  pedir<UsuarioCreado>('/api/usuarios', { method: 'POST', body: JSON.stringify(datos) })
