import { pedir } from './cliente'

/** HU-12.01: los datos fiscales de la empresa que aparecen en las facturas. */
export interface Emisor {
  configurado: boolean
  nit: string | null
  razonSocial: string | null
  nombreComercial: string | null
  direccionFiscal: string | null
  telefono: string | null
  correo: string | null
  modificadoEn: string | null
}

export interface DatosEmisor {
  nit: string
  razonSocial: string
  nombreComercial: string
  direccionFiscal: string
  telefono: string
  correo: string
}

export const CLAVE_EMISOR = ['configuracion', 'emisor'] as const

export const obtenerEmisor = () => pedir<Emisor>('/api/configuracion/emisor')

/** Guarda los datos. 422 `NIT_INVALIDO` si el dígito verificador no corresponde (crit. 2). */
export const guardarEmisor = (datos: DatosEmisor) =>
  pedir<Emisor>('/api/configuracion/emisor', { method: 'PUT', body: JSON.stringify(datos) })
