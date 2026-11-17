import { pedir } from './cliente'

/** HU-12.08: una impresora térmica de la red. El servidor le envía los tickets a `host:puerto`. */
export interface Impresora {
  id: number
  nombre: string
  /** Dirección IP o nombre del equipo en la red. */
  host: string
  puerto: number
  /** Ancho del papel (crit. 1). */
  anchoMm: 58 | 80
  /** Copias de cada factura y de cada cierre de caja (crit. 3). */
  copiasFactura: number
  copiasCierre: number
  modificadoEn: string | null
}

export interface DatosImpresora {
  nombre: string
  host: string
  puerto: number
  anchoMm: 58 | 80
  copiasFactura: number
  copiasCierre: number
}

export interface ResultadoPrueba {
  exitosa: boolean
  mensaje: string
}

export const CLAVE_IMPRESORAS = ['impresoras'] as const

export const listarImpresoras = () => pedir<Impresora[]>('/api/configuracion/impresoras')

export const crearImpresora = (datos: DatosImpresora) =>
  pedir<Impresora>('/api/configuracion/impresoras', { method: 'POST', body: JSON.stringify(datos) })

export const editarImpresora = (id: number, datos: DatosImpresora) =>
  pedir<Impresora>(`/api/configuracion/impresoras/${id}`, { method: 'PUT', body: JSON.stringify(datos) })

/** Crit. 2: imprime la página de prueba. Si la impresora no responde, `exitosa` es `false` con el motivo. */
export const probarImpresora = (id: number) =>
  pedir<ResultadoPrueba>(`/api/configuracion/impresoras/${id}/prueba`, { method: 'POST' })
