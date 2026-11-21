import { useMutation } from '@tanstack/react-query'
import { exportarCatalogo, type FormatoCatalogo } from '../../api/archivoCatalogo'

/**
 * HU-03.12 Exportar el catálogo: todo el catálogo (activos e inactivos) con código, descripción, precio, estado y
 * fecha de la última modificación, en las columnas de la plantilla de carga (crit. 2).
 */
export function ExportarCatalogo() {
  const descarga = useMutation({ mutationFn: (formato: FormatoCatalogo) => exportarCatalogo(formato) })
  return (
    <section className="tarjeta" aria-label="Exportar el catálogo">
      <h2>Exportar el catálogo</h2>
      <p>
        Descarga todos los productos, activos e inactivos. El archivo usa las columnas de la plantilla de carga, así que se
        puede corregir y volver a cargar.
      </p>
      <div className="acciones" role="group" aria-label="Formato de exportación">
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('XLSX')}>
          {descarga.isPending && descarga.variables === 'XLSX' ? 'Exportando…' : 'Exportar a Excel'}
        </button>
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('CSV')}>
          {descarga.isPending && descarga.variables === 'CSV' ? 'Exportando…' : 'Exportar a CSV'}
        </button>
      </div>
      {descarga.error && (
        <p className="error" role="alert">
          {descarga.error.message}
        </p>
      )}
      {descarga.data && <p role="status">Se descargó {descarga.data}.</p>}
    </section>
  )
}
