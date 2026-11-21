import { ErrorDeApi, pedir, type ErrorApi } from './cliente'

/** HU-11.05 crit. 3: resultado de recalcular la cadena de hashes de la bitácora. */
export interface VerificacionBitacora {
  integra: boolean
  registros: number
  /** Registros alterados, eslabones rotos o registros faltantes; vacío si está íntegra. */
  problemas: { secuencia: number; descripcion: string }[]
}

export const verificarBitacora = () => pedir<VerificacionBitacora>('/api/bitacora/verificacion')

/** HU-11.07: los filtros como los escribe el formulario (vacío: sin filtro). `exitoso`: '', 'true' o 'false'. */
export interface FiltrosBitacora {
  desde: string
  hasta: string
  usuarioId: string
  cajaId: string
  modulo: string
  accion: string
  exitoso: '' | 'true' | 'false'
  documento: string
}

/** HU-11.06 crit. 1: un registro de la lista. `usuario` es "Nombre (usuario)". */
export interface RegistroBitacora {
  id: number
  secuencia: number
  ocurridoEn: string
  usuario: string | null
  rol: string | null
  caja: string | null
  modulo: string
  accion: string
  exitoso: boolean
  descripcion: string
}

export interface PaginaBitacora {
  registros: RegistroBitacora[]
  pagina: number
  tamanoPagina: number
  total: number
}

export interface OpcionesBitacora {
  usuarios: { id: number; etiqueta: string }[]
  cajas: { id: number; etiqueta: string }[]
  modulos: string[]
  acciones: string[]
}

/** HU-11.08 crit. 2: el documento relacionado; el de una venta es su factura. */
export interface DocumentoRelacionado {
  tipo: 'FACTURA' | 'CIERRE' | 'PRODUCTO' | 'USUARIO'
  id: number
  etiqueta: string
}

/** HU-11.08 crit. 1: todo lo guardado del evento, con los valores anteriores y nuevos en `cambios`. */
export interface DetalleEvento {
  registro: RegistroBitacora
  entidad: string | null
  entidadId: number | null
  cambios: Record<string, unknown> | null
  documento: DocumentoRelacionado | null
}

export const CLAVE_OPCIONES_BITACORA = ['bitacora', 'opciones'] as const
export const claveDetalleEvento = (id: number) => ['bitacora', 'evento', id] as const

export const rutaBitacora = (filtros: FiltrosBitacora, pagina: number) => {
  const parametros = new URLSearchParams()
  for (const [nombre, valor] of Object.entries(filtros)) if (valor.trim()) parametros.set(nombre, valor.trim())
  if (pagina > 0) parametros.set('pagina', String(pagina))
  const consulta = parametros.toString()
  return `/api/bitacora${consulta ? `?${consulta}` : ''}`
}

/** HU-11.09 crit. 1: los formatos de exportación. */
export type FormatoBitacora = 'XLSX' | 'CSV'

/** HU-11.09: los mismos filtros de la consulta, sin página, más el formato. */
export const rutaExportarBitacora = (filtros: FiltrosBitacora, formato: FormatoBitacora) =>
  rutaBitacora(filtros, 0).replace('/api/bitacora', '/api/bitacora/exportar').replace(/\?|$/, (separador) =>
    `?formato=${formato}${separador === '?' ? '&' : ''}`)

/**
 * HU-11.09: descarga el archivo. La exportación queda en la bitácora (crit. 2) y solo el Administrador puede hacerla
 * (crit. 3: el servidor responde 403 a otro rol). Con filtros inválidos responde el error en JSON.
 */
export async function exportarBitacora(filtros: FiltrosBitacora, formato: FormatoBitacora) {
  const respuesta = await fetch(rutaExportarBitacora(filtros, formato), { credentials: 'include' })
  if (!respuesta.ok) {
    const error: ErrorApi = await respuesta.json().catch(() => ({
      codigo: 'SIN_RESPUESTA',
      mensaje: 'No se pudo exportar la bitácora.',
      detalles: [],
    }))
    throw new ErrorDeApi(respuesta.status, error)
  }
  const nombre =
    /filename="?([^";]+)"?/.exec(respuesta.headers.get('Content-Disposition') ?? '')?.[1] ??
    `bitacora.${formato === 'CSV' ? 'csv' : 'xlsx'}`
  const enlace = document.createElement('a')
  enlace.href = URL.createObjectURL(await respuesta.blob())
  enlace.download = nombre
  document.body.append(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(enlace.href), 1000)
  return nombre
}

/** HU-11.06 y 11.07. El servidor registra cada búsqueda (no los cambios de página) en la bitácora. */
export const buscarEnBitacora = (filtros: FiltrosBitacora, pagina: number) =>
  pedir<PaginaBitacora>(rutaBitacora(filtros, pagina))

export const obtenerOpcionesBitacora = () => pedir<OpcionesBitacora>('/api/bitacora/opciones')

/** HU-11.08. Abrir el detalle también queda en la bitácora. */
export const obtenerDetalleEvento = (id: number) => pedir<DetalleEvento>(`/api/bitacora/${id}`)

/** HU-11.10: el plazo de conservación y el estado del archivo de la bitácora. */
export interface ConservacionBitacora {
  plazoAnios: number
  plazoMinimoAnios: number
  registrosActivos: number
  registrosArchivados: number
  registroMasAntiguo: string | null
  archivadoHasta: string | null
  /** El primer día que ya no se puede archivar: se archiva lo anterior a esta fecha o a una más antigua. */
  archivableAntesDe: string
  /** Los registros anteriores a este día ya cumplieron el plazo; aun así no se eliminan (crit. 3). */
  plazoCumplidoAntesDe: string
}

export const CLAVE_CONSERVACION_BITACORA = ['bitacora', 'conservacion'] as const

export const obtenerConservacion = () => pedir<ConservacionBitacora>('/api/configuracion/bitacora')

/** HU-11.10 crit. 1: solo el Administrador; de 4 a 50 años. */
export const cambiarPlazoConservacion = (plazoAnios: number) =>
  pedir<ConservacionBitacora>('/api/configuracion/bitacora', { method: 'PUT', body: JSON.stringify({ plazoAnios }) })

/** HU-11.10 crit. 2: mueve al archivo lo anterior a `antesDe` (de hace más de 90 días); no borra nada. */
export const archivarBitacora = (antesDe: string) =>
  pedir<{ archivados: number; estado: ConservacionBitacora }>('/api/configuracion/bitacora/archivo', {
    method: 'POST',
    body: JSON.stringify({ antesDe }),
  })
