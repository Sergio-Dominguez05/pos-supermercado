import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  confirmarCarga,
  descargarPlantilla,
  revisarCarga,
  type FormatoCatalogo,
  type RevisionCarga,
} from '../../api/archivoCatalogo'
import { formatoQuetzales } from './textos'

/** Cuántas filas de cada grupo se muestran en la revisión; el resto se resume con su cantidad. */
export const FILAS_VISIBLES = 100

/**
 * HU-03.05 Cargar el catálogo desde un archivo: descargar la plantilla (crit. 1), revisar el archivo (crit. 2 a 4) y
 * confirmar la carga (crit. 5). La bitácora la registra el servidor (crit. 6).
 */
export function CargaCatalogo() {
  const consultas = useQueryClient()
  const [archivo, setArchivo] = useState<File>()
  const plantilla = useMutation({ mutationFn: (formato: FormatoCatalogo) => descargarPlantilla(formato) })
  const revision = useMutation({ mutationFn: (elegido: File) => revisarCarga(elegido) })
  const carga = useMutation({
    mutationFn: ({ elegido, huella }: { elegido: File; huella: string }) => confirmarCarga(elegido, huella),
    onSuccess: () => {
      revision.reset()
      void consultas.invalidateQueries({ queryKey: ['productos'] })
    },
  })

  return (
    <section className="tarjeta formulario" aria-label="Cargar el catálogo">
      <h2>Cargar el catálogo desde un archivo</h2>
      <p>
        Use la plantilla (código, descripción y precio con IVA). Un código que ya existe actualiza su descripción y su
        precio; los nuevos se crean. Nada se guarda hasta que confirme.
      </p>
      <div className="acciones" role="group" aria-label="Descargar la plantilla">
        <button type="button" disabled={plantilla.isPending} onClick={() => plantilla.mutate('XLSX')}>
          Plantilla de Excel
        </button>
        <button type="button" disabled={plantilla.isPending} onClick={() => plantilla.mutate('CSV')}>
          Plantilla CSV
        </button>
      </div>
      <form
        className="en-linea"
        onSubmit={(evento) => {
          evento.preventDefault()
          if (archivo) revision.mutate(archivo)
        }}
      >
        <label>
          Archivo (.xlsx o .csv)
          <input
            name="archivo"
            type="file"
            accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
            onChange={(evento) => {
              setArchivo(evento.target.files?.[0])
              revision.reset()
              carga.reset()
            }}
          />
        </label>
        <button type="submit" disabled={!archivo || revision.isPending}>
          {revision.isPending ? 'Revisando…' : 'Revisar archivo'}
        </button>
      </form>
      {(plantilla.error ?? revision.error ?? carga.error) && (
        <p className="error" role="alert">
          {(plantilla.error ?? revision.error ?? carga.error)?.message}
        </p>
      )}
      {revision.data && archivo && (
        <RevisionDeCarga
          revision={revision.data}
          confirmando={carga.isPending}
          onConfirmar={() => carga.mutate({ elegido: archivo, huella: revision.data.huella })}
        />
      )}
      {carga.data && (
        <p className="exito" role="status">
          Catálogo cargado: {carga.data.creados} creados, {carga.data.actualizados} actualizados,{' '}
          {carga.data.sinCambios} sin cambios
          {carga.data.errores > 0 && ` y ${carga.data.errores} filas con errores que no se cargaron`}.
        </p>
      )}
    </section>
  )
}

const mas = (total: number) =>
  total > FILAS_VISIBLES ? <p><small>Y {total - FILAS_VISIBLES} más.</small></p> : null

/** Crit. 2 a 4: los nuevos, los que se actualizarán (antes y después) y las filas con error y su motivo. */
export function RevisionDeCarga({
  revision,
  confirmando,
  onConfirmar,
}: {
  revision: RevisionCarga
  confirmando: boolean
  onConfirmar: () => void
}) {
  const aCargar = revision.nuevos.length + revision.actualizados.length
  return (
    <div aria-label="Revisión de la carga">
      <dl className="resumen">
        <dt>Filas leídas</dt>
        <dd>{revision.filas}</dd>
        <dt>Nuevos</dt>
        <dd>{revision.nuevos.length}</dd>
        <dt>Se actualizarán</dt>
        <dd>{revision.actualizados.length}</dd>
        <dt>Sin cambios</dt>
        <dd>{revision.sinCambios}</dd>
        <dt>Con errores (no se cargan)</dt>
        <dd className={revision.errores.length > 0 ? 'error' : undefined}>{revision.errores.length}</dd>
      </dl>
      {revision.errores.length > 0 && (
        <details open>
          <summary>Filas con errores</summary>
          <table className="tabla">
            <thead>
              <tr>
                <th>Fila</th>
                <th>Código</th>
                <th>Motivo</th>
              </tr>
            </thead>
            <tbody>
              {revision.errores.slice(0, FILAS_VISIBLES).map((error) => (
                <tr key={error.fila}>
                  <td>{error.fila}</td>
                  <td>{error.codigo || '—'}</td>
                  <td>{error.motivo}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {mas(revision.errores.length)}
        </details>
      )}
      {revision.nuevos.length > 0 && (
        <details>
          <summary>Productos nuevos</summary>
          <table className="tabla">
            <thead>
              <tr>
                <th>Fila</th>
                <th>Código</th>
                <th>Descripción</th>
                <th>Precio</th>
              </tr>
            </thead>
            <tbody>
              {revision.nuevos.slice(0, FILAS_VISIBLES).map((nuevo) => (
                <tr key={nuevo.fila}>
                  <td>{nuevo.fila}</td>
                  <td>{nuevo.codigo}</td>
                  <td>{nuevo.descripcion}</td>
                  <td>{formatoQuetzales(nuevo.precio)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {mas(revision.nuevos.length)}
        </details>
      )}
      {revision.actualizados.length > 0 && (
        <details>
          <summary>Productos que se actualizarán</summary>
          <table className="tabla">
            <thead>
              <tr>
                <th>Fila</th>
                <th>Código</th>
                <th>Descripción</th>
                <th>Precio</th>
              </tr>
            </thead>
            <tbody>
              {revision.actualizados.slice(0, FILAS_VISIBLES).map((cambio) => (
                <tr key={cambio.fila}>
                  <td>{cambio.fila}</td>
                  <td>{cambio.codigo}</td>
                  <td>
                    {cambio.descripcionDespues === null
                      ? 'Sin cambio'
                      : `${cambio.descripcionAntes} → ${cambio.descripcionDespues}`}
                  </td>
                  <td>
                    {cambio.precioDespues === null || cambio.precioAntes === null
                      ? 'Sin cambio'
                      : `${formatoQuetzales(cambio.precioAntes)} → ${formatoQuetzales(cambio.precioDespues)}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {mas(revision.actualizados.length)}
        </details>
      )}
      <button type="button" className="boton" disabled={aCargar === 0 || confirmando} onClick={onConfirmar}>
        {confirmando
          ? 'Cargando…'
          : aCargar === 0
            ? 'No hay productos que cargar'
            : `Confirmar la carga de ${aCargar} ${aCargar === 1 ? 'producto' : 'productos'}`}
      </button>
    </div>
  )
}
