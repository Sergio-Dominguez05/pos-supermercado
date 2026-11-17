import { pedir } from './cliente'

export type AmbienteFel = 'PRUEBAS' | 'PRODUCCION'

/**
 * HU-12.03: la conexión con el certificador FEL tal como la devuelve el servidor. Nunca trae la llave o token, solo
 * si hay una guardada (HU-12.15 crit. 2).
 */
export interface ConexionFel {
  configurada: boolean
  certificador: string | null
  /** HU-08.05 crit. 1: va en la factura impresa. */
  nitCertificador: string | null
  urlServicio: string | null
  usuario: string | null
  secretoGuardado: boolean
  ambiente: AmbienteFel | null
  timeoutMs: number | null
  modificadoEn: string | null
}

/** Lo que envía el formulario. `secreto` vacío conserva la llave guardada. */
export interface DatosConexionFel {
  certificador: string
  /** Opcional: vacío no se guarda. */
  nitCertificador?: string
  urlServicio: string
  usuario: string
  secreto: string
  ambiente: AmbienteFel
  timeoutMs: number
  /** HU-12.03 crit. 3: confirmación explícita para pasar a producción. */
  confirmarProduccion?: boolean
}

/** HU-08.01 crit. 3: resultado de probar la conexión, sin emitir facturas. */
export interface ResultadoPruebaConexion {
  exitosa: boolean
  mensaje: string
}

export const CLAVE_CONEXION_FEL = ['configuracion', 'fel'] as const

export const obtenerConexionFel = () => pedir<ConexionFel>('/api/configuracion/fel')

/** Guarda la conexión. 422 `URL_NO_SEGURA` si no es https y `CONFIRMAR_PRODUCCION` si falta confirmar. */
export const guardarConexionFel = (datos: DatosConexionFel) =>
  pedir<ConexionFel>('/api/configuracion/fel', { method: 'PUT', body: JSON.stringify(datos) })

/** HU-12.03 crit. 2: prueba los datos del formulario sin guardarlos. */
export const probarConexionFel = (datos: DatosConexionFel) =>
  pedir<ResultadoPruebaConexion>('/api/configuracion/fel/probar', { method: 'POST', body: JSON.stringify(datos) })
