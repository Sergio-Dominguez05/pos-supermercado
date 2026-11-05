import { pedir } from './cliente'

/** HU-11.05 crit. 3: resultado de recalcular la cadena de hashes de la bitácora. */
export interface VerificacionBitacora {
  integra: boolean
  registros: number
  /** Registros alterados, eslabones rotos o registros faltantes; vacío si está íntegra. */
  problemas: { secuencia: number; descripcion: string }[]
}

export const verificarBitacora = () => pedir<VerificacionBitacora>('/api/bitacora/verificacion')
