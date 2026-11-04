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

export const crearUsuario = (datos: NuevoUsuario) =>
  pedir<UsuarioCreado>('/api/usuarios', { method: 'POST', body: JSON.stringify(datos) })
