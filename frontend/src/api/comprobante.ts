import { pedir } from './cliente'

/** HU-12.09: el formato del comprobante; `logo` es el PNG en blanco y negro (`data:` URL) o `null`. */
export interface FormatoComprobante {
  encabezado: string | null
  pie: string | null
  logo: string | null
  logoAncho: number | null
  logoAlto: number | null
  modificadoEn: string | null
}

/** Lo que se envía: `logo` es una imagen nueva en Base64 (vacío conserva la actual); `quitarLogo` la elimina. */
export interface SolicitudFormato {
  encabezado: string
  pie: string
  logo: string | null
  quitarLogo: boolean
}

/** Un renglón del ticket, como lo arma el servidor. */
export interface RenglonTicket {
  texto: string
  alineacion: 'IZQUIERDA' | 'CENTRO'
  negrita: boolean
  destacado: boolean
}

/** HU-12.09 crit. 3: una factura de ejemplo con el formato, sin guardarlo. */
export interface VistaPrevia {
  columnas: number
  logo: string | null
  renglones: RenglonTicket[]
}

export const LARGO_MAXIMO_ENCABEZADO = 120
export const LARGO_MAXIMO_PIE = 200
/** El servidor rechaza imágenes de más de 1 MB. */
export const BYTES_MAXIMOS_LOGO = 1_000_000

export const CLAVE_FORMATO_COMPROBANTE = ['configuracion', 'comprobante'] as const

export const obtenerFormatoComprobante = () => pedir<FormatoComprobante>('/api/configuracion/comprobante')

export const guardarFormatoComprobante = (solicitud: SolicitudFormato) =>
  pedir<FormatoComprobante>('/api/configuracion/comprobante', { method: 'PUT', body: JSON.stringify(solicitud) })

export const vistaPreviaComprobante = (solicitud: SolicitudFormato, anchoMm: 58 | 80) =>
  pedir<VistaPrevia>(`/api/configuracion/comprobante/vista-previa?anchoMm=${anchoMm}`, {
    method: 'POST',
    body: JSON.stringify(solicitud),
  })
