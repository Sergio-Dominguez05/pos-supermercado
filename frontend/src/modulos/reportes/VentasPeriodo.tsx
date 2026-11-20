import { useMutation } from '@tanstack/react-query'
import {
  generarReporteVentas,
  type FilaVentasPeriodo,
  type OpcionesDeFiltro,
  type ReporteVentasPeriodo,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'

/** HU-10.02: el reporte de ventas por período. */
export function ReporteDeVentas({ opciones }: { opciones: OpcionesDeFiltro }) {
  // Una mutación y no una consulta: cada generación queda en la bitácora (HU-10.15 crit. 3), así que no se repite sola.
  const generacion = useMutation({ mutationFn: generarReporteVentas })
  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        conAgrupacion
        generando={generacion.isPending}
        onGenerar={generacion.mutate}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && <TablaVentasPeriodo reporte={generacion.data} />}
    </>
  )
}

const celdas = (fila: FilaVentasPeriodo) => (
  <>
    <td>{fila.cantidadVentas}</td>
    <td>{formatoQuetzales(fila.subtotal)}</td>
    <td>{formatoQuetzales(fila.iva)}</td>
    <td>{formatoQuetzales(fila.total)}</td>
    <td>{formatoQuetzales(fila.ticketPromedio)}</td>
  </>
)

/** HU-10.02 crit. 1 a 3: por período, sin las facturas anuladas, con la fila de totales. */
export function TablaVentasPeriodo({ reporte }: { reporte: ReporteVentasPeriodo }) {
  return (
    <section aria-label="Ventas por período">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      {reporte.totales.cantidadVentas === 0 && <p className="tarjeta">No hay ventas con estos filtros.</p>}
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Período</th>
              <th>Ventas</th>
              <th>Subtotal</th>
              <th>IVA</th>
              <th>Total vendido</th>
              <th>Ticket promedio</th>
            </tr>
          </thead>
          <tbody>
            {reporte.filas.map((fila) => (
              <tr key={fila.desde}>
                <td>{fila.periodo}</td>
                {celdas(fila)}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">Total</th>
              {celdas(reporte.totales)}
            </tr>
          </tfoot>
        </table>
      </div>
      <p>
        <small>Las facturas anuladas no se suman a las ventas.</small>
      </p>
    </section>
  )
}
