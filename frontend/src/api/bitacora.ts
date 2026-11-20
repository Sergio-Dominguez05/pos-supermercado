import { pedir } from './cliente'

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

/** HU-11.06 y 11.07. El servidor registra cada búsqueda (no los cambios de página) en la bitácora. */
export const buscarEnBitacora = (filtros: FiltrosBitacora, pagina: number) =>
  pedir<PaginaBitacora>(rutaBitacora(filtros, pagina))

export const obtenerOpcionesBitacora = () => pedir<OpcionesBitacora>('/api/bitacora/opciones')

/** HU-11.08. Abrir el detalle también queda en la bitácora. */
export const obtenerDetalleEvento = (id: number) => pedir<DetalleEvento>(`/api/bitacora/${id}`)
