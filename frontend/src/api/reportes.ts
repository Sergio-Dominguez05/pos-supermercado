import { pedir } from './cliente'

/** Una opción de una lista de filtros: el valor que se envía y el texto que se muestra. */
export interface OpcionFiltro {
  valor: string
  etiqueta: string
}

/** HU-10.01 crit. 3: un rango rápido, calculado con la fecha del servidor. */
export interface RangoRapido {
  codigo: 'HOY' | 'AYER' | 'ESTA_SEMANA' | 'ESTE_MES' | 'MES_ANTERIOR'
  etiqueta: string
  desde: string
  hasta: string
}

export interface OpcionesDeFiltro {
  hoy: string
  rangos: RangoRapido[]
  cajas: OpcionFiltro[]
  cajeros: OpcionFiltro[]
  tiposTarjeta: OpcionFiltro[]
  agrupaciones: OpcionFiltro[]
}

/** HU-10.01 crit. 4: el encabezado común, con los filtros aplicados. */
export interface EncabezadoReporte {
  titulo: string
  filtros: { etiqueta: string; valor: string }[]
  generadoEn: string
  generadoPor: string
}

export type Agrupacion = 'DIA' | 'SEMANA' | 'MES'

export interface FilaVentasPeriodo {
  periodo: string
  desde: string
  hasta: string
  cantidadVentas: number
  subtotal: number
  iva: number
  total: number
  ticketPromedio: number
}

/** HU-10.02: ventas por período, sin las facturas anuladas. */
export interface ReporteVentasPeriodo {
  encabezado: EncabezadoReporte
  agrupacion: Agrupacion
  filas: FilaVentasPeriodo[]
  totales: FilaVentasPeriodo
}

/** Los filtros tal como se piden: fechas `yyyy-MM-dd`; caja y cajero vacíos = todos. */
export interface FiltrosVentas {
  desde: string
  hasta: string
  cajaId: string
  cajeroId: string
  agrupacion: Agrupacion
}

export const CLAVE_FILTROS_REPORTE = ['reportes', 'filtros'] as const

export const obtenerOpcionesDeFiltro = () => pedir<OpcionesDeFiltro>('/api/reportes/filtros')

/** Solo los filtros con valor van en la consulta. */
export const consultaDe = (filtros: Record<string, string>) => {
  const consulta = new URLSearchParams()
  for (const [nombre, valor] of Object.entries(filtros)) if (valor) consulta.set(nombre, valor)
  return consulta.toString()
}

/** Cada generación queda en la bitácora (HU-10.15 crit. 3). */
export const generarReporteVentas = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPeriodo>(`/api/reportes/ventas?${consultaDe({ ...filtros })}`)
