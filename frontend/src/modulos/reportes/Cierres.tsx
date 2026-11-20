import { useMutation } from '@tanstack/react-query'
import { generarReporteCierres, type OpcionesDeFiltro, type ReporteCierres } from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { fechaHora, textoDiferencia } from './filtros'

/** HU-10.08: los cierres de caja del período, con faltantes y sobrantes. */
export function ReporteDeCierres({ opciones }: { opciones: OpcionesDeFiltro }) {
  const generacion = useMutation({ mutationFn: generarReporteCierres })
  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        conDiferencias
        generando={generacion.isPending}
        onGenerar={generacion.mutate}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && <TablaCierres reporte={generacion.data} />}
    </>
  )
}

/** HU-10.08 crit. 1 y 2: cada cierre con sus totales, la diferencia y su justificación. */
export function TablaCierres({ reporte }: { reporte: ReporteCierres }) {
  return (
    <section aria-label="Cierres de caja">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      <dl className="tarjeta resumen" aria-label="Faltantes y sobrantes">
        <dt>Cierres</dt>
        <dd>{reporte.cierres.length}</dd>
        <dt>Con diferencia</dt>
        <dd>{reporte.cierresConDiferencia}</dd>
        <dt>Total de faltantes</dt>
        <dd className={reporte.totalFaltantes > 0 ? 'error' : undefined}>{formatoQuetzales(reporte.totalFaltantes)}</dd>
        <dt>Total de sobrantes</dt>
        <dd>{formatoQuetzales(reporte.totalSobrantes)}</dd>
      </dl>
      {reporte.cierres.length === 0 ? (
        <p className="tarjeta">No hay cierres de caja con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Cerrado</th>
                <th>Caja</th>
                <th>Cajero</th>
                <th>Registrado con tarjeta</th>
                <th>Cierre de lote</th>
                <th>Diferencia</th>
                <th>Justificación</th>
              </tr>
            </thead>
            <tbody>
              {reporte.cierres.map((cierre) => (
                <tr key={cierre.turnoId}>
                  <td>{fechaHora(cierre.cerradoEn)}</td>
                  <td>{cierre.caja}</td>
                  <td>{cierre.cajero}</td>
                  <td>{formatoQuetzales(cierre.totalRegistrado)}</td>
                  <td>{formatoQuetzales(cierre.totalLote)}</td>
                  <td className={cierre.diferencia < 0 ? 'error' : undefined}>{textoDiferencia(cierre.diferencia)}</td>
                  <td className="texto-largo">{cierre.justificacion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p>
        <small>La diferencia es el cierre de lote menos lo registrado en el POS: negativa es un faltante.</small>
      </p>
    </section>
  )
}
