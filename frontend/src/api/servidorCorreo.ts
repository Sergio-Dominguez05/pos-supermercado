import type { ResultadoPruebaConexion } from './conexionFel'
import { pedir } from './cliente'

export type SeguridadCorreo = 'STARTTLS' | 'SSL' | 'NINGUNA'

/** HU-08.15: el servidor de correo tal como lo devuelve el servidor. Nunca trae la contraseña, solo si hay una. */
export interface ServidorCorreo {
  configurado: boolean
  servidor: string | null
  puerto: number | null
  seguridad: SeguridadCorreo | null
  usuario: string | null
  secretoGuardado: boolean
  remitente: string | null
  nombreRemitente: string | null
  timeoutMs: number | null
  modificadoEn: string | null
}

/** Lo que envía el formulario. `usuario` vacío: el servidor no pide autenticación; `secreto` vacío conserva la guardada. */
export interface DatosServidorCorreo {
  servidor: string
  puerto: number
  seguridad: SeguridadCorreo
  usuario: string
  secreto: string
  remitente: string
  nombreRemitente: string
  timeoutMs: number
}

/** El puerto usual de cada seguridad, para proponerlo al cambiarla. */
export const PUERTO_USUAL: Record<SeguridadCorreo, number> = { STARTTLS: 587, SSL: 465, NINGUNA: 25 }

export const CLAVE_SERVIDOR_CORREO = ['configuracion', 'correo'] as const

export const obtenerServidorCorreo = () => pedir<ServidorCorreo>('/api/configuracion/correo')

export const guardarServidorCorreo = (datos: DatosServidorCorreo) =>
  pedir<ServidorCorreo>('/api/configuracion/correo', { method: 'PUT', body: JSON.stringify(datos) })

/** Envía un correo de prueba a `destinatario` con los datos del formulario, sin guardarlos. */
export const probarServidorCorreo = (servidor: DatosServidorCorreo, destinatario: string) =>
  pedir<ResultadoPruebaConexion>('/api/configuracion/correo/probar', {
    method: 'POST',
    body: JSON.stringify({ servidor, destinatario }),
  })
