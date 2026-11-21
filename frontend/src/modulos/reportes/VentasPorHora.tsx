import { useMutation } from '@tanstack/react-query'
import {
  generarReporteHoras,
  type Exportacion,
  type FilaVentasHora,
  type OpcionesDeFiltro,
  type ReporteVentasPorHora,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { alturaDeBarra, horaCorta } from './filtros'

/** HU-10.11: las ventas por hora, para ver las horas de más movimiento y asignar cajeros. */
export function ReporteDeHoras({ opciones }: { opciones: OpcionesDeFiltro }) {
  const generacion = useMutation({ mutationFn: generarReporteHoras })
  return (
    <>
      <FormularioFiltros opciones={opciones} generando={generacion.isPending} onGenerar={generacion.mutate} />
      <p>
        <small>Elija un solo día para ver sus ventas, o un período para ver el promedio por día de cada hora.</small>
      </p>
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && generacion.variables && (
        <TablaVentasPorHora
          reporte={generacion.data}
          exportacion={{ codigo: 'VENTAS_HORA', filtros: generacion.variables }}
        />
      )}
    </>
  )
}

/**
 * HU-10.11 crit. 3: una barra por hora. En un período la altura es el promedio de ventas por día (crit. 2); en un día,
 * la cantidad de ventas. La hora pico se resalta.
 */
export function GraficaPorHora({ reporte }: { reporte: ReporteVentasPorHora }) {
  const valor = (fila: FilaVentasHora) => (reporte.promedio ? fila.promedioVentas : fila.cantidadVentas)
  const maximo = Math.max(0, ...reporte.filas.map(valor))
  return (
    <figure className="tarjeta grafica-horas">
      <figcaption>
        {reporte.promedio ? `Ventas promedio por día en cada hora (${reporte.dias} días)` : 'Ventas en cada hora'}
      </figcaption>
      <div className="barras" role="img" aria-label="Gráfica de barras de las ventas por hora">
        {reporte.filas.map((fila) => (
          <div
            key={fila.hora}
            className={fila.hora === reporte.horaPico ? 'barra pico' : 'barra'}
            title={`${fila.etiqueta}: ${valor(fila)} ventas · ${formatoQuetzales(
              reporte.promedio ? fila.promedioTotal : fila.totalVendido,
            )}`}
          >
            <span className="relleno" style={{ height: `${alturaDeBarra(valor(fila), maximo)}%` }} />
            <span className="eje">{horaCorta(fila.hora ?? 0)}</span>
          </div>
        ))}
      </div>
    </figure>
  )
}

const celdas = (fila: FilaVentasHora, promedio: boolean) => (
  <>
    <td>{fila.cantidadVentas}</td>
    <td>{formatoQuetzales(fila.totalVendido)}</td>
    {promedio && <td>{fila.promedioVentas.toFixed(1)}</td>}
    {promedio && <td>{formatoQuetzales(fila.promedioTotal)}</td>}
  </>
)

/** HU-10.11 crit. 1 y 2: la cantidad de ventas y el total por hora; en un período, también el promedio por día. */
export function TablaVentasPorHora({
  reporte,
  exportacion,
}: {
  reporte: ReporteVentasPorHora
  exportacion?: Exportacion
}) {
  const pico = reporte.horaPico === null ? null : reporte.filas[reporte.horaPico]
  return (
    <section aria-label="Ventas por hora">
      <EncabezadoDeReporte encabezado={reporte.encabezado} exportacion={exportacion} />
      {pico ? (
        <p role="status">
          Hora de más ventas: <strong>{pico.etiqueta}</strong> ({pico.cantidadVentas} ventas)
        </p>
      ) : (
        <p className="tarjeta">No hay ventas con estos filtros.</p>
      )}
      <GraficaPorHora reporte={reporte} />
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Hora</th>
              <th>Ventas</th>
              <th>Total vendido</th>
              {reporte.promedio && <th>Ventas por día</th>}
              {reporte.promedio && <th>Promedio por día</th>}
            </tr>
          </thead>
          <tbody>
            {reporte.filas.map((fila) => (
              <tr key={fila.hora} className={fila.hora === reporte.horaPico ? 'seleccionada' : undefined}>
                <td>{fila.etiqueta}</td>
                {celdas(fila, reporte.promedio)}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">Total</th>
              {celdas(reporte.totales, reporte.promedio)}
            </tr>
          </tfoot>
        </table>
      </div>
      <p>
        <small>
          Ventas finalizadas sin las facturas anuladas, por la hora de finalización.
          {reporte.promedio && ' El promedio divide entre todos los días del período.'}
        </small>
      </p>
    </section>
  )
}
