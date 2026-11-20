import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  generarDetalleIva,
  generarReporteIva,
  type DetalleFacturasIva,
  type DiaIva,
  type FiltrosVentas,
  type OpcionesDeFiltro,
  type ReporteIva as DatosReporteIva,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { fechaHora } from './filtros'

const dia = (fecha: string) => fecha.split('-').reverse().join('/')

/** HU-10.07: el IVA de las facturas certificadas por día y, a pedido, el detalle por factura (crit. 3). */
export function ReporteIva({ opciones }: { opciones: OpcionesDeFiltro }) {
  const [filtros, setFiltros] = useState<FiltrosVentas | null>(null)
  const generacion = useMutation({ mutationFn: generarReporteIva })
  const detalle = useMutation({ mutationFn: generarDetalleIva })

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
        <TablaIva
          reporte={generacion.data}
          cargandoDetalle={detalle.isPending}
          onVerFacturas={() => detalle.mutate(filtros)}
        />
      )}
      <ErrorDeReporte error={detalle.error} />
      {detalle.data && <TablaFacturasIva detalle={detalle.data} />}
    </>
  )
}

const celdas = (fila: DiaIva) => (
  <>
    <td>{fila.facturas}</td>
    <td>{formatoQuetzales(fila.baseImponible)}</td>
    <td>{formatoQuetzales(fila.iva)}</td>
    <td>{formatoQuetzales(fila.total)}</td>
  </>
)

/** HU-10.07 crit. 1, 2 y 4: base imponible, IVA y total de las facturas certificadas, sin las anuladas. */
export function TablaIva({
  reporte,
  cargandoDetalle = false,
  onVerFacturas,
}: {
  reporte: DatosReporteIva
  cargandoDetalle?: boolean
  onVerFacturas: () => void
}) {
  return (
    <section aria-label="IVA cobrado">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      {reporte.sinCertificar.facturas > 0 && (
        <p className="tarjeta pendiente" role="status">
          {reporte.sinCertificar.facturas === 1
            ? 'Hay 1 factura del período sin certificar'
            : `Hay ${reporte.sinCertificar.facturas} facturas del período sin certificar`}{' '}
          ({formatoQuetzales(reporte.sinCertificar.total)}): no se incluyen porque todavía no constan ante la SAT.
        </p>
      )}
      {reporte.dias.length === 0 ? (
        <p className="tarjeta">No hay facturas certificadas con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Día</th>
                <th>Facturas</th>
                <th>Base imponible</th>
                <th>IVA</th>
                <th>Total vendido</th>
              </tr>
            </thead>
            <tbody>
              {reporte.dias.map((fila) => (
                <tr key={fila.fecha}>
                  <td>{dia(fila.fecha as string)}</td>
                  {celdas(fila)}
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total</th>
                {celdas(reporte.total)}
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <div className="acciones">
        <button type="button" className="boton-texto" disabled={cargandoDetalle} onClick={onVerFacturas}>
          {cargandoDetalle ? 'Cargando facturas…' : 'Ver el detalle por factura'}
        </button>
      </div>
      <p>
        <small>Solo facturas certificadas, por su fecha de emisión; las anuladas no se incluyen.</small>
      </p>
    </section>
  )
}

/** HU-10.07 crit. 3: cada factura certificada con su serie, número, autorización y montos. */
export function TablaFacturasIva({ detalle }: { detalle: DetalleFacturasIva }) {
  return (
    <section aria-label="Detalle de IVA por factura">
      <EncabezadoDeReporte encabezado={detalle.encabezado} />
      {detalle.facturas.length === 0 ? (
        <p className="tarjeta">No hay facturas certificadas con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Emitida</th>
                <th>Serie y número</th>
                <th>Autorización</th>
                <th>Cliente</th>
                <th>Caja</th>
                <th>Base imponible</th>
                <th>IVA</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {detalle.facturas.map((factura) => (
                <tr key={factura.facturaId}>
                  <td>{fechaHora(factura.emitidaEn)}</td>
                  <td>
                    {factura.serie}-{factura.numero}
                  </td>
                  <td>
                    <small>{factura.autorizacion}</small>
                  </td>
                  <td>
                    {factura.receptorId} · {factura.receptorNombre}
                  </td>
                  <td>{factura.caja}</td>
                  <td>{formatoQuetzales(factura.baseImponible)}</td>
                  <td>{formatoQuetzales(factura.iva)}</td>
                  <td>{formatoQuetzales(factura.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
