import { useMutation } from '@tanstack/react-query'
import {
  generarReporteCajas,
  type Exportacion,
  type FilaVentasCaja,
  type OpcionesDeFiltro,
  type ReporteVentasPorCaja,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { textoParticipacion } from './filtros'

/** HU-10.04: las ventas de cada caja en el período, para saber cuáles se usan más. */
export function ReporteDeCajas({ opciones }: { opciones: OpcionesDeFiltro }) {
  const generacion = useMutation({ mutationFn: generarReporteCajas })
  return (
    <>
      <FormularioFiltros opciones={opciones} generando={generacion.isPending} onGenerar={generacion.mutate} />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && generacion.variables && (
        <TablaVentasPorCaja
          reporte={generacion.data}
          exportacion={{ codigo: 'VENTAS_CAJA', filtros: generacion.variables }}
        />
      )}
    </>
  )
}

const celdas = (fila: FilaVentasCaja) => (
  <>
    <td>{fila.cantidadVentas}</td>
    <td>{formatoQuetzales(fila.totalVendido)}</td>
    <td>{fila.turnos}</td>
    <td>{formatoQuetzales(fila.ticketPromedio)}</td>
  </>
)

/**
 * HU-10.04 crit. 1: por caja, ventas, total vendido y turnos. Crit. 2: todas las cajas del mismo período, de la que
 * más vendió a la que menos, con la barra de su participación para compararlas de un vistazo.
 */
export function TablaVentasPorCaja({
  reporte,
  exportacion,
}: {
  reporte: ReporteVentasPorCaja
  exportacion?: Exportacion
}) {
  return (
    <section aria-label="Ventas por caja">
      <EncabezadoDeReporte encabezado={reporte.encabezado} exportacion={exportacion} />
      {reporte.filas.length === 0 ? (
        <p className="tarjeta">No hay cajas con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Caja</th>
                <th>Ventas</th>
                <th>Total vendido</th>
                <th>Turnos</th>
                <th>Ticket promedio</th>
                <th>Participación</th>
              </tr>
            </thead>
            <tbody>
              {reporte.filas.map((fila) => (
                <tr key={fila.cajaId}>
                  <td>{fila.caja}</td>
                  {celdas(fila)}
                  <td className="participacion">
                    <meter
                      min={0}
                      max={100}
                      value={fila.participacion}
                      aria-label={`Participación de ${fila.caja}`}
                    />
                    {textoParticipacion(fila.participacion)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total</th>
                {celdas(reporte.totales)}
                <td>{textoParticipacion(reporte.totales.participacion)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <p>
        <small>
          Ventas y total sin las facturas anuladas. Los turnos cuentan por la fecha en que se abrieron; la participación
          es la parte del total vendido.
        </small>
      </p>
    </section>
  )
}
