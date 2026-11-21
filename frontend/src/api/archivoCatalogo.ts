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

/** HU-03.05 crit. 1: la plantilla de carga, con las columnas código, descripción y precio. */
export const descargarPlantilla = (formato: FormatoCatalogo) =>
  descargarArchivo(
    `/api/productos/plantilla?formato=${formato}`,
    `plantilla_catalogo.${formato === 'CSV' ? 'csv' : 'xlsx'}`,
  )

/** HU-03.05 crit. 2: un producto que se creará. */
export interface ProductoNuevo {
  fila: number
  codigo: string
  descripcion: string
  precio: number
}

/** HU-03.05 crit. 3: un producto que ya existe y cambiará; `null` en lo que no cambia. */
export interface ProductoActualizado {
  fila: number
  productoId: number
  codigo: string
  descripcionAntes: string | null
  descripcionDespues: string | null
  precioAntes: number | null
  precioDespues: number | null
}

/** HU-03.05 crit. 4: una fila que no se cargará y por qué. */
export interface FilaConError {
  fila: number
  codigo: string
  motivo: string
}

/** HU-03.05 crit. 2: lo que haría la carga; `huella` identifica el archivo revisado (crit. 5). */
export interface RevisionCarga {
  huella: string
  filas: number
  nuevos: ProductoNuevo[]
  actualizados: ProductoActualizado[]
  sinCambios: number
  errores: FilaConError[]
}

export interface ResultadoCarga {
  creados: number
  actualizados: number
  sinCambios: number
  errores: number
}

/** Envía el archivo como `multipart/form-data` (sin el `Content-Type` de JSON que pone `pedir`). */
async function enviarArchivo<T>(ruta: string, archivo: File): Promise<T> {
  const datos = new FormData()
  datos.append('archivo', archivo)
  const respuesta = await fetch(ruta, { method: 'POST', body: datos, credentials: 'include' })
  if (!respuesta.ok) {
    const error: ErrorApi = await respuesta.json().catch(() => ({
      codigo: 'SIN_RESPUESTA',
      mensaje: 'No se pudo enviar el archivo.',
      detalles: [],
    }))
    throw new ErrorDeApi(respuesta.status, error)
  }
  return respuesta.json()
}

/** HU-03.05 crit. 2: revisa el archivo sin guardar nada. */
export const revisarCarga = (archivo: File) => enviarArchivo<RevisionCarga>('/api/productos/carga/revision', archivo)

/** HU-03.05 crit. 5: carga el mismo archivo que se revisó (el servidor lo comprueba con la huella). */
export const confirmarCarga = (archivo: File, huella: string) =>
  enviarArchivo<ResultadoCarga>(`/api/productos/carga?huella=${encodeURIComponent(huella)}`, archivo)
