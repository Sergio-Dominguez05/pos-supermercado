import { ErrorDeApi, pedir, type ErrorApi } from './cliente'

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
  /** HU-10.09 crit. 2: quienes pueden anular facturas o ya anularon alguna. */
  anuladores: OpcionFiltro[]
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
  /** HU-10.01 crit. 2: `DEBITO`, `CREDITO` o vacío (todas); solo en los reportes de tarjetas. */
  tipoTarjeta: string
  /** HU-10.08 crit. 3: solo los cierres con diferencia. */
  soloConDiferencias: boolean
  /** HU-10.09 crit. 2: el usuario que anuló, o vacío (cualquiera). */
  anuladaPorId: string
  /** HU-10.05 crit. 2: ordenar los productos por cantidad (por omisión) o por monto. */
  ordenarPor?: OrdenProductos
  /** HU-10.05 crit. 3: cuántos productos mostrar; vacío = 20. */
  limite?: string
}

export type OrdenProductos = 'CANTIDAD' | 'MONTO'

/** HU-10.05: un producto en su posición, o la fila de totales (`posicion` y `productoId` nulos). */
export interface FilaProductoVendido {
  posicion: number | null
  productoId: number | null
  codigo: string | null
  descripcion: string
  cantidad: number
  monto: number
}

export interface ReporteProductosMasVendidos {
  encabezado: EncabezadoReporte
  ordenarPor: OrdenProductos
  limite: number
  /** Cuántos productos distintos se vendieron en el período, aunque no se muestren todos. */
  productosVendidos: number
  filas: FilaProductoVendido[]
  totales: FilaProductoVendido
}

/** Los filtros propios del reporte de productos más vendidos. */
const deProductos = (filtros: FiltrosVentas) => ({ ordenarPor: filtros.ordenarPor ?? '', limite: filtros.limite ?? '' })

export const CLAVE_FILTROS_REPORTE = ['reportes', 'filtros'] as const

/** HU-10.12: una caja con su turno abierto. */
export interface CajaAbierta {
  turnoId: number
  caja: string
  cajero: string
  abiertaDesde: string
}

/** HU-10.12 crit. 1 y 2: el resumen de hoy y lo que requiere atención. */
export interface PanelDelDia {
  fecha: string
  actualizadoEn: string
  cantidadVentas: number
  totalVendido: number
  ticketPromedio: number
  cajasAbiertas: CajaAbierta[]
  ventasPorTarjeta: { tipo: 'DEBITO' | 'CREDITO'; etiqueta: string; transacciones: number; monto: number }[]
  alertas: { contingenciaPendientes: number; codigosNoEncontrados: number; cierresConDiferencia: number }
}

export const CLAVE_PANEL_DEL_DIA = ['reportes', 'panel'] as const

/** HU-10.12 crit. 3: cada cuánto se vuelve a pedir el panel, sin recargar la pantalla. */
export const INTERVALO_PANEL_MS = 60_000

export const obtenerPanelDelDia = () => pedir<PanelDelDia>('/api/reportes/panel')

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

/** HU-10.04: una fila por caja, o la de totales (`cajaId` nulo). */
export interface FilaVentasCaja {
  cajaId: number | null
  caja: string
  cantidadVentas: number
  totalVendido: number
  turnos: number
  ticketPromedio: number
  /** El porcentaje del total vendido, con un decimal (45.5 = 45.5 %). */
  participacion: number
}

export interface ReporteVentasPorCaja {
  encabezado: EncabezadoReporte
  filas: FilaVentasCaja[]
  totales: FilaVentasCaja
}

/** HU-10.11: una hora del día (0 a 23), o la fila de totales (`hora` nula). */
export interface FilaVentasHora {
  hora: number | null
  etiqueta: string
  cantidadVentas: number
  totalVendido: number
  /** Ventas por día en esa hora, con un decimal; igual a `cantidadVentas` en un solo día. */
  promedioVentas: number
  promedioTotal: number
}

export interface ReporteVentasPorHora {
  encabezado: EncabezadoReporte
  dias: number
  /** Crit. 2: verdadero si es un período (más de un día) y se muestra el promedio por día. */
  promedio: boolean
  filas: FilaVentasHora[]
  totales: FilaVentasHora
  /** La hora con más ventas, o `null` si no hubo ventas. */
  horaPico: number | null
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

export const generarReporteCajas = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPorCaja>(`/api/reportes/cajas?${consultaDe(sinAgrupacion(filtros))}`)

export const generarReporteHoras = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPorHora>(`/api/reportes/horas?${consultaDe(sinAgrupacion(filtros))}`)

export const generarReporteProductos =(filtros: FiltrosVentas) =>
  pedir<ReporteProductosMasVendidos>(
    `/api/reportes/productos?${consultaDe({ ...sinAgrupacion(filtros), ...deProductos(filtros) })}`,
  )

/** El detalle usa las fechas y la caja del resumen; el cajero es el de la fila elegida. */
export const generarDetalleCajero = (cajeroId: number, filtros: FiltrosVentas) =>
  pedir<DetalleVentasCajero>(
    `/api/reportes/cajeros/${cajeroId}/ventas?${consultaDe({ ...sinAgrupacion(filtros), cajeroId: '' })}`,
  )

export type TipoTarjeta = 'DEBITO' | 'CREDITO'

/** HU-10.06: un tipo de tarjeta, o la fila de totales (`tipo` nulo). */
export interface FilaTarjeta {
  tipo: TipoTarjeta | null
  etiqueta: string
  transacciones: number
  monto: number
}

export interface ReporteTarjetas {
  encabezado: EncabezadoReporte
  filas: FilaTarjeta[]
  total: FilaTarjeta
  /** Crit. 2: los pagos de facturas anuladas, que no se suman al total. */
  anulados: FilaTarjeta[]
  totalAnulado: FilaTarjeta
}

/** HU-10.06 crit. 3: una transacción, con su autorización y los últimos cuatro dígitos si se registraron. */
export interface PagoConTarjeta {
  pagoId: number
  fecha: string
  ventaNumero: number
  caja: string
  cajero: string
  autorizacion: string
  ultimos4: string | null
  monto: number
  anulada: boolean
}

export interface DetallePagosTarjeta {
  encabezado: EncabezadoReporte
  pagos: PagoConTarjeta[]
}

/** HU-10.07: un día con facturas certificadas, o el total (`fecha` nula). */
export interface DiaIva {
  fecha: string | null
  facturas: number
  baseImponible: number
  iva: number
  total: number
}

export interface ReporteIva {
  encabezado: EncabezadoReporte
  dias: DiaIva[]
  total: DiaIva
  /** Las facturas del período que aún no están certificadas: todavía no cuentan para la SAT. */
  sinCertificar: { facturas: number; total: number }
}

export interface FacturaIva {
  facturaId: number
  emitidaEn: string
  serie: string
  numero: string
  autorizacion: string
  receptorId: string
  receptorNombre: string
  caja: string
  baseImponible: number
  iva: number
  total: number
}

export interface DetalleFacturasIva {
  encabezado: EncabezadoReporte
  facturas: FacturaIva[]
}

export const generarReporteTarjetas = (filtros: FiltrosVentas) =>
  pedir<ReporteTarjetas>(
    `/api/reportes/tarjetas?${consultaDe({ ...sinAgrupacion(filtros), tipoTarjeta: filtros.tipoTarjeta })}`,
  )

/** El detalle usa los filtros del resumen; el tipo es el de la fila elegida. */
export const generarDetalleTarjeta = (tipo: TipoTarjeta, filtros: FiltrosVentas) =>
  pedir<DetallePagosTarjeta>(`/api/reportes/tarjetas/${tipo}/pagos?${consultaDe(sinAgrupacion(filtros))}`)

export const generarReporteIva = (filtros: FiltrosVentas) =>
  pedir<ReporteIva>(`/api/reportes/iva?${consultaDe(sinAgrupacion(filtros))}`)

export const generarDetalleIva = (filtros: FiltrosVentas) =>
  pedir<DetalleFacturasIva>(`/api/reportes/iva/facturas?${consultaDe(sinAgrupacion(filtros))}`)

/** HU-10.08 crit. 1: un cierre de caja; la diferencia es lote − registrado (negativa = faltante). */
export interface CierreDeCaja {
  turnoId: number
  abiertoEn: string
  cerradoEn: string
  caja: string
  cajero: string
  cerradoPor: string | null
  totalRegistrado: number
  totalLote: number
  diferencia: number
  justificacion: string | null
}

export interface ReporteCierres {
  encabezado: EncabezadoReporte
  cierres: CierreDeCaja[]
  cierresConDiferencia: number
  /** Crit. 2: los faltantes, como monto positivo, y los sobrantes. */
  totalFaltantes: number
  totalSobrantes: number
}

/** HU-10.09 crit. 1: una factura anulada. */
export interface FacturaAnulada {
  facturaId: number
  emitidaEn: string
  anuladaEn: string
  serie: string
  numero: string
  ventaNumero: number
  caja: string
  monto: number
  motivo: string
  cajero: string
  anuladaPor: string
}

export interface ReporteAnuladas {
  encabezado: EncabezadoReporte
  facturas: FacturaAnulada[]
  cantidad: number
  monto: number
}

export const generarReporteCierres = (filtros: FiltrosVentas) =>
  pedir<ReporteCierres>(
    `/api/reportes/cierres?${consultaDe({
      ...sinAgrupacion(filtros),
      soloConDiferencias: filtros.soloConDiferencias ? 'true' : '',
    })}`,
  )

export const generarReporteAnuladas = (filtros: FiltrosVentas) =>
  pedir<ReporteAnuladas>(
    `/api/reportes/anuladas?${consultaDe({ ...sinAgrupacion(filtros), anuladaPorId: filtros.anuladaPorId })}`,
  )

/** HU-10.13 y HU-10.14: los reportes y detalles que se exportan, con el código que usa el servidor. */
export type CodigoExportable =
  | 'VENTAS_PERIODO'
  | 'VENTAS_CAJERO'
  | 'DETALLE_CAJERO'
  | 'VENTAS_CAJA'
  | 'PRODUCTOS'
  | 'VENTAS_HORA'
  | 'TARJETAS'
  | 'DETALLE_TARJETA'
  | 'IVA'
  | 'DETALLE_IVA'
  | 'CIERRES'
  | 'ANULADAS'

export type FormatoExportacion = 'PDF' | 'XLSX'

/**
 * Lo que hace falta para exportar lo que se ve: el reporte, los filtros con que se generó y, en los detalles, el
 * cajero o el tipo de tarjeta de la fila elegida.
 */
export interface Exportacion {
  codigo: CodigoExportable
  filtros: FiltrosVentas
  cajeroId?: number
  tipoTarjeta?: TipoTarjeta
}

/** Los mismos filtros que usó la pantalla, para que el archivo tenga los mismos datos (HU-10.13 crit. 3). */
export const rutaExportacion = (
  { codigo, filtros, cajeroId, tipoTarjeta }: Exportacion,
  formato: FormatoExportacion,
) => {
  const propios = (): Record<string, string> => {
    switch (codigo) {
      case 'VENTAS_PERIODO':
        return { agrupacion: filtros.agrupacion }
      case 'TARJETAS':
        return { tipoTarjeta: filtros.tipoTarjeta }
      case 'CIERRES':
        return { soloConDiferencias: filtros.soloConDiferencias ? 'true' : '' }
      case 'ANULADAS':
        return { anuladaPorId: filtros.anuladaPorId }
      case 'PRODUCTOS':
        return deProductos(filtros)
      case 'DETALLE_CAJERO':
        return { cajeroId: String(cajeroId ?? '') }
      case 'DETALLE_TARJETA':
        return { tipoTarjeta: tipoTarjeta ?? '' }
      default:
        return {}
    }
  }
  return `/api/reportes/exportar/${codigo}?${consultaDe({ formato, ...sinAgrupacion(filtros), ...propios() })}`
}

/** El nombre que el servidor da al archivo, o uno genérico. */
export const nombreDeArchivo = (disposicion: string | null, formato: FormatoExportacion) =>
  /filename="?([^";]+)"?/.exec(disposicion ?? '')?.[1] ?? `reporte.${formato === 'PDF' ? 'pdf' : 'xlsx'}`

/**
 * Pide el archivo y lo descarga. La exportación queda en la bitácora (HU-10.15 crit. 3); con filtros inválidos el
 * servidor responde con el error en JSON, como el resto de la API.
 */
export async function descargarReporte(exportacion: Exportacion, formato: FormatoExportacion) {
  const respuesta = await fetch(rutaExportacion(exportacion, formato), { credentials: 'include' })
  if (!respuesta.ok) {
    const error: ErrorApi = await respuesta.json().catch(() => ({
      codigo: 'SIN_RESPUESTA',
      mensaje: 'No se pudo exportar el reporte.',
      detalles: [],
    }))
    throw new ErrorDeApi(respuesta.status, error)
  }
  const nombre = nombreDeArchivo(respuesta.headers.get('Content-Disposition'), formato)
  const enlace = document.createElement('a')
  enlace.href = URL.createObjectURL(await respuesta.blob())
  enlace.download = nombre
  document.body.append(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(enlace.href), 1000)
  return nombre
}

/** Cada generación queda en la bitácora (HU-10.15 crit. 3). */
export const generarReporteVentas = (filtros: FiltrosVentas) =>
  pedir<ReporteVentasPeriodo>(
    `/api/reportes/ventas?${consultaDe({ ...sinAgrupacion(filtros), agrupacion: filtros.agrupacion })}`,
  )
