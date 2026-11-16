import { pedir } from './cliente'

/** HU-09.01: turno de caja. No tiene fondo inicial porque no se manejan pagos en efectivo. */
export interface Turno {
  id: number
  cajaId: number
  cajaNombre: string
  usuarioNombre: string
  estado: 'ABIERTO' | 'CERRADO'
  /** Fecha y hora del servidor, en ISO 8601. */
  abiertoEn: string
  /** Si el turno es del usuario de esta sesión. */
  propio: boolean
}

export const CLAVE_TURNO_ACTUAL = ['turnos', 'actual'] as const

/** Turno abierto en la caja de este equipo, o `null` si está cerrada o el equipo no tiene caja (204). */
export const turnoActual = async () => (await pedir<Turno | undefined>('/api/turnos/actual')) ?? null

/** Abre el turno del usuario en la caja de este equipo. */
export const abrirTurno = () => pedir<Turno>('/api/turnos', { method: 'POST' })
