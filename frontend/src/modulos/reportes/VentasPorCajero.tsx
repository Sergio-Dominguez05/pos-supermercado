import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  generarDetalleCajero,
  generarReporteCajeros,
  type DetalleVentasCajero,
  type Exportacion,
  type FilaVentasCajero,
  type FiltrosVentas,
  type OpcionesDeFiltro,
  type ReporteVentasPorCajero,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { ESTADOS_FACTURA, fechaHora } from './filtros'

/** HU-10.03: el resumen por cajero y, desde cada fila, el detalle de sus ventas (crit. 2). */
export function ReporteDeCajeros({ opciones }: { opciones: OpcionesDeFiltro }) {
  const [filtros, setFiltros] = useState<FiltrosVentas | null>(null)
  const generacion = useMutation({ mutationFn: generarReporteCajeros })
  const detalle = useMutation({
    mutationFn: ({ cajeroId, filtros }: { cajeroId: number; filtros: FiltrosVentas }) =>
      generarDetalleCajero(cajeroId, filtros),
  })

  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        generando={generacion.isPending}
        onGenerar={(elegidos) => {
          setFiltros(elegidos)
          detalle.reset()
          generacion.mutate(elegidos)
        }}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && filtros && (
        <TablaVentasPorCajero
          reporte={generacion.data}
          exportacion={{ codigo: 'VENTAS_CAJERO', filtros }}
          cargandoDetalle={detalle.isPending}
          onVerDetalle={(cajeroId) => detalle.mutate({ cajeroId, filtros })}
        />
      )}
      <ErrorDeReporte error={detalle.error} />
      {detalle.data && detalle.variables && (
        <TablaDetalleCajero
          detalle={detalle.data}
          exportacion={{
            codigo: 'DETALLE_CAJERO',
            filtros: detalle.variables.filtros,
            cajeroId: detalle.variables.cajeroId,
          }}
        />
      )}
    </>
  )
}

const celdas = (fila: FilaVentasCajero) => (
  <>
    <td>{fila.cantidadVentas}</td>
    <td>{formatoQuetzales(fila.totalVendido)}</td>
    <td>{fila.ventasCanceladas}</td>
    <td>{fila.lineasEliminadas}</td>
    <td>
      {fila.facturasAnuladas}
      {fila.facturasAnuladas > 0 && <small> · {formatoQuetzales(fila.montoAnulado)}</small>}
    </td>
  </>
)

/** HU-10.03 crit. 1: por cajero, ventas, total, canceladas, líneas eliminadas y facturas anuladas. */
export function TablaVentasPorCajero({
  reporte,
  exportacion,
  cargandoDetalle = false,
  onVerDetalle,
}: {
  reporte: ReporteVentasPorCajero
  exportacion?: Exportacion
  cargandoDetalle?: boolean
  onVerDetalle: (cajeroId: number) => void
}) {
  return (
    <section aria-label="Ventas por cajero">
      <EncabezadoDeReporte encabezado={reporte.encabezado} exportacion={exportacion} />
      {reporte.filas.length === 0 ? (
        <p className="tarjeta">No hay actividad de cajeros con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Cajero</th>
                <th>Ventas</th>
                <th>Total vendido</th>
                <th>Canceladas</th>
                <th>Líneas eliminadas</th>
                <th>Facturas anuladas</th>
                <th>
                  <span className="solo-lector">Detalle</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {reporte.filas.map((fila) => (
                <tr key={fila.cajeroId}>
                  <td>{fila.cajero}</td>
                  {celdas(fila)}
                  <td>
                    <button
                      type="button"
                      className="boton-texto"
                      disabled={cargandoDetalle}
                      aria-label={`Ver las ventas de ${fila.cajero}`}
                      onClick={() => onVerDetalle(fila.cajeroId as number)}
                    >
                      Ver ventas
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total</th>
                {celdas(reporte.totales)}
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <p>
        <small>
          Ventas y total sin las facturas anuladas. Las canceladas cuentan por la fecha en que se iniciaron; las líneas
          eliminadas, por la fecha en que se eliminaron.
        </small>
      </p>
    </section>
  )
}

/** HU-10.03 crit. 2: las ventas finalizadas y canceladas de un cajero, en orden. */
export function TablaDetalleCajero({
  detalle,
  exportacion,
}: {
  detalle: DetalleVentasCajero
  exportacion?: Exportacion
}) {
  return (
    <section aria-label="Detalle de ventas del cajero">
      <EncabezadoDeReporte encabezado={detalle.encabezado} exportacion={exportacion} />
      {detalle.ventas.length === 0 ? (
        <p className="tarjeta">El cajero no tiene ventas finalizadas ni canceladas con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Venta</th>
                <th>Fecha</th>
                <th>Caja</th>
                <th>Estado</th>
                <th>Total</th>
                <th>Factura</th>
                <th>Líneas eliminadas</th>
              </tr>
            </thead>
            <tbody>
              {detalle.ventas.map((venta) => (
                <tr key={venta.ventaId}>
                  <td>N.º {venta.numero}</td>
                  <td>{fechaHora(venta.fecha)}</td>
                  <td>{venta.caja}</td>
                  <td>{venta.estado === 'CANCELADA' ? 'Cancelada' : 'Finalizada'}</td>
                  <td>{venta.total === null ? '—' : formatoQuetzales(venta.total)}</td>
                  <td>
                    {venta.estadoFactura === null
                      ? '—'
                      : [venta.factura, ESTADOS_FACTURA[venta.estadoFactura] ?? venta.estadoFactura]
                          .filter(Boolean)
                          .join(' · ')}
                  </td>
                  <td>{venta.lineasEliminadas}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
