import { pedir } from './cliente'
import type { FacturaDeVenta, ResultadoImpresion } from './pagos'

/** HU-08.06 crit. 3 y 4: una venta cobrada cuya factura rechazó el certificador, pendiente de facturar. */
export interface FacturaRechazada {
  ventaId: number
  ventaNumero: number
  cajaNombre: string
  cajero: string
  emitidaEn: string
  total: number
  receptorId: string
  receptorNombre: string
  tipo: 'RECEPTOR' | 'MONTOS' | 'EMISOR' | 'OTRO'
  /** Crit. 1: el motivo en lenguaje comprensible. */
  motivo: string
  corregibleEnCaja: boolean
  codigo: string | null
  mensajeCertificador: string | null
  /** Crit. 4: la respuesta completa del certificador. */
  respuesta: string | null
  rechazadaEn: string
}

export const CLAVE_FACTURAS_RECHAZADAS = ['facturas', 'rechazadas'] as const

export const listarFacturasRechazadas = () => pedir<FacturaRechazada[]>('/api/facturas/rechazadas')

/** El supervisor reenvía la factura con el mismo receptor, cuando ya se corrigió la causa (crit. 3). */
export const reenviarFacturaRechazada = (ventaId: number) =>
  pedir<FacturaDeVenta>(`/api/facturas/rechazadas/${ventaId}/reenviar`, { method: 'POST' })

/**
 * Crit. 2: el cajero corrige el cliente de su venta ya cobrada y reenvía la factura, sin volver a cobrar. `null`
 * la reenvía a Consumidor Final.
 */
export const reenviarFacturaDesdeCaja = (ventaId: number, clienteId: number | null) =>
  pedir<FacturaDeVenta>(`/api/ventas/${ventaId}/factura/reenviar`, {
    method: 'POST',
    body: JSON.stringify({ clienteId }),
  })

/** HU-08.10: una factura emitida, como se busca para anularla. */
export interface FacturaEmitida {
  facturaId: number
  ventaId: number
  ventaNumero: number
  turnoId: number
  /** Decisión D2: solo se anula mientras el turno de la venta siga abierto. */
  turnoAbierto: boolean
  cajaNombre: string
  estado: 'CERTIFICADA' | 'ANULADA'
  serie: string
  numero: string
  autorizacion: string
  fechaCertificacion: string
  emitidaEn: string
  total: number
  receptorId: string
  receptorNombre: string
  motivoAnulacion: string | null
  anuladaEn: string | null
}

/** Crit. 1: por número, serie o autorización (`buscar`) y por fecha de emisión (`fecha`, AAAA-MM-DD). */
export const buscarFacturas = (buscar: string, fecha: string) => {
  const parametros = new URLSearchParams()
  if (buscar.trim()) parametros.set('buscar', buscar.trim())
  if (fecha) parametros.set('fecha', fecha)
  const consulta = parametros.toString()
  return pedir<FacturaEmitida[]>(`/api/facturas${consulta ? `?${consulta}` : ''}`)
}

/** Crit. 2 y 3: anula la factura con su motivo; el certificador debe aceptarla. */
export const anularFactura = (facturaId: number, motivo: string) =>
  pedir<FacturaEmitida>(`/api/facturas/${facturaId}/anular`, { method: 'POST', body: JSON.stringify({ motivo }) })

/** HU-08.12 crit. 2: las facturas que esta sesión puede reimprimir (el cajero, las de su turno). */
export const listarReimprimibles = (buscar: string, fecha: string) => {
  const parametros = new URLSearchParams()
  if (buscar.trim()) parametros.set('buscar', buscar.trim())
  if (fecha) parametros.set('fecha', fecha)
  const consulta = parametros.toString()
  return pedir<FacturaEmitida[]>(`/api/facturas/reimprimibles${consulta ? `?${consulta}` : ''}`)
}

/** HU-08.12 crit. 1: la última factura de la caja de este equipo (F9). */
export const reimprimirUltima = () => pedir<ResultadoImpresion>('/api/facturas/ultima/reimprimir', { method: 'POST' })

export const reimprimirFactura = (facturaId: number) =>
  pedir<ResultadoImpresion>(`/api/facturas/${facturaId}/reimprimir`, { method: 'POST' })

/** HU-08.07 crit. 3: si hay comprobantes en contingencia, para el aviso de la pantalla de venta. */
export interface EstadoContingencia {
  enContingencia: boolean
  pendientes: number
}

/** HU-08.09 crit. 3: un comprobante pendiente de certificar, con el error de su último intento. */
export interface PendienteContingencia {
  ventaId: number
  ventaNumero: number
  cajaNombre: string
  estado: 'PENDIENTE' | 'CONTINGENCIA'
  numeroAcceso: number | null
  emitidaEn: string
  total: number
  intentos: number
  ultimoError: string | null
  proximoIntento: string
  dias: number
  /** El último día para certificarlo. */
  vence: string
  alerta: boolean
}

/** HU-08.09 crit. 1 y 2: cuántos hay, el más antiguo y si se acerca el plazo de la SAT. */
export interface ResumenContingencia {
  pendientes: number
  masAntigua: string | null
  diasMasAntigua: number
  venceMasAntigua: string | null
  alerta: boolean
  diasMaximos: number
  diaDeAlerta: number
  lista: PendienteContingencia[]
}

export interface RondaCertificacion {
  procesadas: number
  certificadas: number
  rechazadas: number
  quedan: number
  ocupado: boolean
  mensaje: string
}

export const CLAVE_ESTADO_CONTINGENCIA = ['facturas', 'contingencia', 'estado'] as const
export const CLAVE_CONTINGENCIA = ['facturas', 'contingencia'] as const

export const obtenerEstadoContingencia = () => pedir<EstadoContingencia>('/api/facturas/contingencia/estado')

export const obtenerContingencia = () => pedir<ResumenContingencia>('/api/facturas/contingencia')

/** HU-08.08 crit. 4: certificar ahora los comprobantes pendientes, sin esperar el proceso automático. */
export const certificarContingencia = () =>
  pedir<RondaCertificacion>('/api/facturas/contingencia/certificar', { method: 'POST' })
