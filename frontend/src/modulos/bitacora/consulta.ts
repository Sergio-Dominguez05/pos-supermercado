import type { DocumentoRelacionado, FiltrosBitacora } from '../../api/bitacora'
import { diaEnGuatemala } from '../caja/cierres'

/** HU-11.06, decisión de Sergio: la bitácora abre con los registros de hoy, en hora de Guatemala. */
export const filtrosDeHoy = (ahora: Date = new Date()): FiltrosBitacora => {
  const hoy = diaEnGuatemala(ahora)
  return { desde: hoy, hasta: hoy, usuarioId: '', cajaId: '', modulo: '', accion: '', exitoso: '', documento: '' }
}

export const rotuloBitacora = (pagina: number, tamano: number, total: number, enPagina: number) =>
  total === 0
    ? 'No hay registros con estos filtros.'
    : `Mostrando ${pagina * tamano + 1}–${pagina * tamano + enPagina} de ${total} ${total === 1 ? 'registro' : 'registros'}`

/** El permiso que pide la pantalla de cada documento y su ruta (HU-11.08 crit. 2: "según mis permisos"). */
const DOCUMENTOS: Record<DocumentoRelacionado['tipo'], { permiso: string; ruta: (id: number) => string }> = {
  FACTURA: { permiso: 'FACTURA_ANULAR', ruta: (id) => `/facturas/${id}` },
  CIERRE: { permiso: 'CAJA_VER_CIERRES', ruta: (id) => `/caja/cierres/${id}` },
  PRODUCTO: { permiso: 'PRODUCTO_CONSULTAR', ruta: (id) => `/catalogo/${id}` },
  USUARIO: { permiso: 'USUARIO_ADMINISTRAR', ruta: (id) => `/usuarios/${id}/editar` },
}

/** La ruta del documento relacionado si el usuario puede abrirlo; si no, `null`. */
export const rutaDocumento = (documento: DocumentoRelacionado, permisos: readonly string[]) => {
  const destino = DOCUMENTOS[documento.tipo]
  return destino && permisos.includes(destino.permiso) ? destino.ruta(documento.id) : null
}

/** Una fila del detalle guardado: el campo y, si cambió, su valor anterior y el nuevo; si no, el valor tal cual. */
export interface FilaCambio {
  campo: string
  antes?: string
  despues?: string
  valor?: string
}

const texto = (valor: unknown) => (typeof valor === 'string' ? valor : JSON.stringify(valor))

/** HU-11.08 crit. 1: el detalle guardado (`cambios`) en filas, con los valores anteriores y nuevos. */
export const filasDeCambios = (cambios: Record<string, unknown> | null): FilaCambio[] =>
  Object.entries(cambios ?? {}).map(([campo, valor]) => {
    if (valor && typeof valor === 'object' && 'antes' in valor && 'despues' in valor) {
      const cambio = valor as { antes: unknown; despues: unknown }
      return { campo, antes: texto(cambio.antes), despues: texto(cambio.despues) }
    }
    return { campo, valor: texto(valor) }
  })

export const fechaYHoraConSegundos = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'America/Guatemala' })
