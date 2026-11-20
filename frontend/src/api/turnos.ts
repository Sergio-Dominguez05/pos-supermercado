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

/** HU-09.08 crit. 3: lo cobrado con tarjeta en el turno; las facturas anuladas se descuentan y se muestran aparte. */
export interface ResumenDeTurno {
  ventas: number
  debito: number
  credito: number
  anuladas: number
  totalAnulado: number
  /** Lo que debe coincidir con el cierre de lote de la terminal. */
  totalRegistrado: number
  /** Comprobantes del turno todavía en contingencia (HU-08.07). */
  contingencias: number
}

/** HU-09.08 crit. 5 y HU-09.09: el cierre guardado. */
export interface CierreDeTurno {
  turnoId: number
  cajaId: number
  cajaNombre: string
  usuarioId: number
  usuarioNombre: string
  abiertoEn: string
  cerradoEn: string
  cerradoPor: string
  totalRegistrado: number
  totalLote: number
  diferencia: number
  justificacion: string | null
}

export interface ResultadoImpresionCierre {
  impresa: boolean
  mensaje: string | null
}

export interface CierreHecho {
  comprobante: { cierre: CierreDeTurno; resumen: ResumenDeTurno }
  impresion: ResultadoImpresionCierre
}

export const CLAVE_RESUMEN_CIERRE = ['turnos', 'actual', 'cierre'] as const
export const CLAVE_CIERRES = ['turnos', 'cierres'] as const

export const obtenerResumenCierre = () => pedir<ResumenDeTurno>('/api/turnos/actual/cierre')

/** HU-09.08 y 09.09: cierra la caja con el total del cierre de lote y, si hay diferencia, su justificación. */
export const cerrarCaja = (totalLote: number, justificacion: string) =>
  pedir<CierreHecho>('/api/turnos/actual/cerrar', {
    method: 'POST',
    body: JSON.stringify({ totalLote, justificacion: justificacion.trim() || null }),
  })

/** HU-09.10 crit. 3: los cierres anteriores (el cajero, los suyos). */
export const listarCierres = () => pedir<CierreDeTurno[]>('/api/turnos/cierres')

export const reimprimirCierre = (turnoId: number) =>
  pedir<ResultadoImpresionCierre>(`/api/turnos/${turnoId}/cierre/reimprimir`, { method: 'POST' })
