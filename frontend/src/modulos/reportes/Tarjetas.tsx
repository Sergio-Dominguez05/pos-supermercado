import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  generarDetalleTarjeta,
  generarReporteTarjetas,
  type DetallePagosTarjeta,
  type FilaTarjeta,
  type FiltrosVentas,
  type OpcionesDeFiltro,
  type ReporteTarjetas,
  type TipoTarjeta,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { fechaHora } from './filtros'

/** HU-10.06: los ingresos por tipo de tarjeta y, desde cada tipo, sus transacciones (crit. 3). */
export function ReporteDeTarjetas({ opciones }: { opciones: OpcionesDeFiltro }) {
  const [filtros, setFiltros] = useState<FiltrosVentas | null>(null)
  const generacion = useMutation({ mutationFn: generarReporteTarjetas })
  const detalle = useMutation({
    mutationFn: ({ tipo, filtros }: { tipo: TipoTarjeta; filtros: FiltrosVentas }) =>
      generarDetalleTarjeta(tipo, filtros),
  })

  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        conTarjeta
        generando={generacion.isPending}
        onGenerar={(elegidos) => {
          setFiltros(elegidos)
          detalle.reset()
          generacion.mutate(elegidos)
        }}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && filtros && (
        <TablaTarjetas
          reporte={generacion.data}
          cargandoDetalle={detalle.isPending}
          onVerDetalle={(tipo) => detalle.mutate({ tipo, filtros })}
        />
      )}
      <ErrorDeReporte error={detalle.error} />
      {detalle.data && <TablaDetalleTarjeta detalle={detalle.data} />}
    </>
  )
}

/** HU-10.06 crit. 1 y 2: débito y crédito; los pagos de facturas anuladas, aparte. */
export function TablaTarjetas({
  reporte,
  cargandoDetalle = false,
  onVerDetalle,
}: {
  reporte: ReporteTarjetas
  cargandoDetalle?: boolean
  onVerDetalle: (tipo: TipoTarjeta) => void
}) {
  const anulado = (tipo: TipoTarjeta | null) => reporte.anulados.find((fila) => fila.tipo === tipo)
  const fila = (datos: FilaTarjeta, encabezado = false) => (
    <>
      {encabezado ? <th scope="row">{datos.etiqueta}</th> : <td>{datos.etiqueta}</td>}
      <td>{datos.transacciones}</td>
      <td>{formatoQuetzales(datos.monto)}</td>
    </>
  )
  return (
    <section aria-label="Ingresos por tipo de tarjeta">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Tipo de tarjeta</th>
              <th>Transacciones</th>
              <th>Total recibido</th>
              <th>De facturas anuladas</th>
              <th>
                <span className="solo-lector">Detalle</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {reporte.filas.map((datos) => {
              const anulados = anulado(datos.tipo)
              return (
                <tr key={datos.etiqueta}>
                  {fila(datos)}
                  <td>
                    {anulados && anulados.transacciones > 0
                      ? `${anulados.transacciones} · ${formatoQuetzales(anulados.monto)}`
                      : '—'}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="boton-texto"
                      disabled={cargandoDetalle}
                      aria-label={`Ver las transacciones con tarjeta de ${datos.etiqueta.toLowerCase()}`}
                      onClick={() => onVerDetalle(datos.tipo as TipoTarjeta)}
                    >
                      Ver transacciones
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr>
              {fila(reporte.total, true)}
              <td>
                {reporte.totalAnulado.transacciones > 0
                  ? `${reporte.totalAnulado.transacciones} · ${formatoQuetzales(reporte.totalAnulado.monto)}`
                  : '—'}
              </td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>
      <p>
        <small>
          Pagos de las ventas finalizadas en el período. Los de facturas anuladas se muestran aparte y no se suman al
          total.
        </small>
      </p>
    </section>
  )
}

/** HU-10.06 crit. 3: cada transacción con su número de autorización, para buscarla en el banco. */
export function TablaDetalleTarjeta({ detalle }: { detalle: DetallePagosTarjeta }) {
  return (
    <section aria-label="Detalle de transacciones con tarjeta">
      <EncabezadoDeReporte encabezado={detalle.encabezado} />
      {detalle.pagos.length === 0 ? (
        <p className="tarjeta">No hay transacciones con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Venta</th>
                <th>Caja</th>
                <th>Cajero</th>
                <th>Autorización</th>
                <th>Tarjeta</th>
                <th>Monto</th>
              </tr>
            </thead>
            <tbody>
              {detalle.pagos.map((pago) => (
                <tr key={pago.pagoId}>
                  <td>{fechaHora(pago.fecha)}</td>
                  <td>N.º {pago.ventaNumero}</td>
                  <td>{pago.caja}</td>
                  <td>{pago.cajero}</td>
                  <td>{pago.autorizacion}</td>
                  <td>{pago.ultimos4 ? `•••• ${pago.ultimos4}` : '—'}</td>
                  <td>
                    {formatoQuetzales(pago.monto)}
                    {pago.anulada && <small className="error"> · factura anulada</small>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
