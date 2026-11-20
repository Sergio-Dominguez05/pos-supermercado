import type { FiltrosVentas, OpcionesDeFiltro } from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-10.15 crit. 1 y 2: el Administrador y el Supervisor; los cajeros no tienen el permiso. */
export const PERMISO_REPORTES = 'REPORTE_CONSULTAR'

/** Los reportes disponibles, en el orden de las pestañas. */
export const REPORTES = [
  { codigo: 'VENTAS_PERIODO', titulo: 'Ventas por período' },
  { codigo: 'VENTAS_CAJERO', titulo: 'Ventas por cajero' },
  { codigo: 'VENTAS_CAJA', titulo: 'Ventas por caja' },
  { codigo: 'TARJETAS', titulo: 'Ingresos por tarjeta' },
  { codigo: 'IVA', titulo: 'IVA cobrado' },
  { codigo: 'CIERRES', titulo: 'Cierres de caja' },
  { codigo: 'ANULADAS', titulo: 'Facturas anuladas' },
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
    tipoTarjeta: '',
    soloConDiferencias: false,
    anuladaPorId: '',
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

/** HU-10.04: la participación con un decimal, como la calcula el servidor. */
export const textoParticipacion = (participacion: number) => `${participacion.toFixed(1)} %`

/** La diferencia con su signo y si es faltante o sobrante (lote − registrado). */
export const textoDiferencia = (diferencia: number) =>
  diferencia === 0
    ? formatoQuetzales(0)
    : diferencia < 0
      ? `−${formatoQuetzales(-diferencia)} (faltante)`
      : `+${formatoQuetzales(diferencia)} (sobrante)`
