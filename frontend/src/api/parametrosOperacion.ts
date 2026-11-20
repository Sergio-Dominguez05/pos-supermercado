import { pedir } from './cliente'

/** HU-12.13: parámetros de control de caja y venta. */
export interface ParametrosOperacion {
  cantidadMaximaPorLinea: number
  cierreCiego: boolean
}

export const CLAVE_PARAMETROS_OPERACION = ['configuracion', 'parametros-operacion'] as const

export const obtenerParametrosOperacion = () =>
  pedir<ParametrosOperacion>('/api/configuracion/parametros')

export const guardarParametrosOperacion = (datos: ParametrosOperacion) =>
  pedir<ParametrosOperacion>('/api/configuracion/parametros', {
    method: 'PUT',
    body: JSON.stringify(datos),
  })
