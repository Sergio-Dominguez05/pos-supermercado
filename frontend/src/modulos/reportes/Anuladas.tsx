import { useMutation } from '@tanstack/react-query'
import { generarReporteAnuladas, type OpcionesDeFiltro, type ReporteAnuladas } from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'
import { fechaHora } from './filtros'

/** HU-10.09: las facturas anuladas en el período (por fecha de anulación). */
export function ReporteDeAnuladas({ opciones }: { opciones: OpcionesDeFiltro }) {
  const generacion = useMutation({ mutationFn: generarReporteAnuladas })
  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        conAnulador
        generando={generacion.isPending}
        onGenerar={generacion.mutate}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && <TablaAnuladas reporte={generacion.data} />}
    </>
  )
}

/** HU-10.09 crit. 1: cada anulación con sus fechas, su factura, el monto, el motivo, el cajero y quién anuló. */
export function TablaAnuladas({ reporte }: { reporte: ReporteAnuladas }) {
  return (
    <section aria-label="Facturas anuladas">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      {reporte.facturas.length === 0 ? (
        <p className="tarjeta">No hay facturas anuladas con estos filtros.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Emitida</th>
                <th>Anulada</th>
                <th>Serie y número</th>
                <th>Monto</th>
                <th>Motivo</th>
                <th>Cajero que emitió</th>
                <th>Anulada por</th>
              </tr>
            </thead>
            <tbody>
              {reporte.facturas.map((factura) => (
                <tr key={factura.facturaId}>
                  <td>{fechaHora(factura.emitidaEn)}</td>
                  <td>{fechaHora(factura.anuladaEn)}</td>
                  <td>
                    {factura.serie}-{factura.numero}
                    <small> · venta N.º {factura.ventaNumero}</small>
                  </td>
                  <td>{formatoQuetzales(factura.monto)}</td>
                  <td className="texto-largo">{factura.motivo}</td>
                  <td>{factura.cajero}</td>
                  <td>{factura.anuladaPor}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row" colSpan={3}>
                  {reporte.cantidad === 1 ? '1 factura anulada' : `${reporte.cantidad} facturas anuladas`}
                </th>
                <td>{formatoQuetzales(reporte.monto)}</td>
                <td colSpan={3} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  )
}
