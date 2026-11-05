import { ErrorDeApi, pedir } from './cliente'

export interface UsuarioSesion {
  username: string
  nombreCompleto: string
  rol: 'ADMIN' | 'SUPERVISOR' | 'CAJERO'
  rolNombre: string
  credencialTemporal: boolean
  permisos: string[]
}

// El token de sesión viaja en una cookie HttpOnly que pone el backend; el frontend nunca lo ve.
export const iniciarSesion = (username: string, contrasena: string) =>
  pedir<UsuarioSesion>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, contrasena }),
  })

export const iniciarSesionConPin = (username: string, pin: string) =>
  pedir<UsuarioSesion>('/api/auth/login-pin', {
    method: 'POST',
    body: JSON.stringify({ username, pin }),
  })

export interface CambioCredencial {
  actual: string
  nueva: string
  confirmacion: string
}

/** HU-01.13: cambia la contraseña (o el PIN, si es Cajero) del usuario de la sesión. */
export const cambiarCredencial = (cambio: CambioCredencial) =>
  pedir<void>('/api/auth/credencial', { method: 'PUT', body: JSON.stringify(cambio) })

/** HU-01.16: cierra la sesión en el servidor, que además borra la cookie. */
export const cerrarSesion = () => pedir<void>('/api/auth/logout', { method: 'POST' })

/** Usuario de la sesión actual, o `null` si no hay sesión. */
export async function obtenerSesion(): Promise<UsuarioSesion | null> {
  try {
    return await pedir<UsuarioSesion>('/api/auth/sesion')
  } catch (error) {
    if (error instanceof ErrorDeApi && error.estado === 401) return null
    throw error
  }
}
