import type { FiltrosVentas, OpcionesDeFiltro } from '../../api/reportes'

/** HU-10.15 crit. 1 y 2: el Administrador y el Supervisor; los cajeros no tienen el permiso. */
export const PERMISO_REPORTES = 'REPORTE_CONSULTAR'

/** Los reportes disponibles, en el orden de las pestañas. */
export const REPORTES = [
  { codigo: 'VENTAS_PERIODO', titulo: 'Ventas por período' },
  { codigo: 'VENTAS_CAJERO', titulo: 'Ventas por cajero' },
] as const

export type CodigoReporte = (typeof REPORTES)[number]['codigo']

/** Los filtros con que abre la pantalla: el mes en curso, por día. */
export const filtrosIniciales = (opciones: OpcionesDeFiltro): FiltrosVentas => {
  const mes = opciones.rangos.find((rango) => rango.codigo === 'ESTE_MES')
  return {
    desde: mes?.desde ?? opciones.hoy,
    hasta: mes?.hasta ?? opciones.hoy,
    cajaId: '',
    cajeroId: '',
    agrupacion: 'DIA',
  }
}

/** Fecha y hora en hora de Guatemala, como en el resto del POS. */
export const fechaHora = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Guatemala',
  })

export const ESTADOS_FACTURA: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  CERTIFICADA: 'Certificada',
  CONTINGENCIA: 'En contingencia',
  RECHAZADA: 'Rechazada',
  ANULADA: 'Anulada',
}
