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

export const crearUsuario = (datos: NuevoUsuario) =>
  pedir<UsuarioCreado>('/api/usuarios', { method: 'POST', body: JSON.stringify(datos) })
