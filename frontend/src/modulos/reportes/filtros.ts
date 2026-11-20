import type { FiltrosVentas, OpcionesDeFiltro } from '../../api/reportes'

/** HU-10.15 crit. 1 y 2: el Administrador y el Supervisor; los cajeros no tienen el permiso. */
export const PERMISO_REPORTES = 'REPORTE_CONSULTAR'

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
