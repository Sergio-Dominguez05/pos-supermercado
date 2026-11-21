import { ErrorDeApi, type ErrorApi } from './cliente'

/** HU-03.12 y HU-03.05: el catálogo en archivos de Excel o CSV. */
export type FormatoCatalogo = 'XLSX' | 'CSV'

/** El permiso de las operaciones masivas sobre el catálogo (exportar y cargar), del Administrador. */
export const PERMISO_ARCHIVO_CATALOGO = 'CATALOGO_IMPORTAR'

/** Pide un archivo al servidor y lo descarga con el nombre que él le da. */
export async function descargarArchivo(ruta: string, nombrePorOmision: string) {
  const respuesta = await fetch(ruta, { credentials: 'include' })
  if (!respuesta.ok) {
    const error: ErrorApi = await respuesta.json().catch(() => ({
      codigo: 'SIN_RESPUESTA',
      mensaje: 'No se pudo descargar el archivo.',
      detalles: [],
    }))
    throw new ErrorDeApi(respuesta.status, error)
  }
  const nombre = /filename="?([^";]+)"?/.exec(respuesta.headers.get('Content-Disposition') ?? '')?.[1] ?? nombrePorOmision
  const enlace = document.createElement('a')
  enlace.href = URL.createObjectURL(await respuesta.blob())
  enlace.download = nombre
  document.body.append(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(enlace.href), 1000)
  return nombre
}

export const rutaExportarCatalogo = (formato: FormatoCatalogo) => `/api/productos/exportar?formato=${formato}`

/** HU-03.12: todo el catálogo; la exportación queda en la bitácora (crit. 3). */
export const exportarCatalogo = (formato: FormatoCatalogo) =>
  descargarArchivo(rutaExportarCatalogo(formato), `catalogo.${formato === 'CSV' ? 'csv' : 'xlsx'}`)
