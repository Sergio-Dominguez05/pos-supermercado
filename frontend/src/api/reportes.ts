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

/** HU-10.03 crit. 1: una fila por cajero con actividad, o la de totales (`cajeroId` nulo). */
export interface FilaVentasCajero {
  cajeroId: number | null
  cajero: string
  cantidadVentas: number
  totalVendido: number
  ventasCanceladas: number
  lineasEliminadas: number
  facturasAnuladas: number
  montoAnulado: number
}

export interface ReporteVentasPorCajero {
  encabezado: EncabezadoReporte
  filas: FilaVentasCajero[]
  totales: FilaVentasCajero
}

/** HU-10.03 crit. 2: una venta finalizada o cancelada del cajero. */
export interface VentaDeCajero {
  ventaId: number
  numero: number
  fecha: string
  caja: string
  estado: 'FINALIZADA' | 'CANCELADA'
  /** Nulo en una venta cancelada: no llegó a fijar sus totales. */
  total: number | null
  estadoFactura: string | null
  factura: string | null
  lineasEliminadas: number
}

export interface DetalleVentasCajero {
  encabezado: EncabezadoReporte
  ventas: VentaDeCajero[]
}

/** Los filtros de los reportes sin agrupación. */
const sinAgrupacion = ({ desde, hasta, cajaId, cajeroId }: FiltrosVentas) => ({ desde, hasta, cajaId, cajeroId })

export const generarReporteCajeros = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPorCajero>(`/api/reportes/cajeros?${consultaDe(sinAgrupacion(filtros))}`)

/** El detalle usa las fechas y la caja del resumen; el cajero es el de la fila elegida. */
export const generarDetalleCajero = (cajeroId: number, filtros: FiltrosVentas) =>
  pedir<DetalleVentasCajero>(
    `/api/reportes/cajeros/${cajeroId}/ventas?${consultaDe({ ...sinAgrupacion(filtros), cajeroId: '' })}`,
  )

/** Cada generación queda en la bitácora (HU-10.15 crit. 3). */
export const generarReporteVentas = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPeriodo>(`/api/reportes/ventas?${consultaDe({ ...filtros })}`)
