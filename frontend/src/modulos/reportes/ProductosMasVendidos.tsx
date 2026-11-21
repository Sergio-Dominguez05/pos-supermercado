import { useMutation } from '@tanstack/react-query'
import {
  generarReporteProductos,
  type Exportacion,
  type OpcionesDeFiltro,
  type ReporteProductosMasVendidos,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { EncabezadoDeReporte, ErrorDeReporte, FormularioFiltros } from './ComunesReporte'

/** HU-10.05: los productos más vendidos del período, para apoyar compras y promociones. */
export function ReporteDeProductos({ opciones }: { opciones: OpcionesDeFiltro }) {
  const generacion = useMutation({ mutationFn: generarReporteProductos })
  return (
    <>
      <FormularioFiltros
        opciones={opciones}
        conProductos
        generando={generacion.isPending}
        onGenerar={generacion.mutate}
      />
      <ErrorDeReporte error={generacion.error} />
      {generacion.data && generacion.variables && (
        <TablaProductosMasVendidos
          reporte={generacion.data}
          exportacion={{ codigo: 'PRODUCTOS', filtros: generacion.variables }}
        />
      )}
    </>
  )
}

/**
 * HU-10.05 crit. 1: por producto, código, descripción, cantidad y monto vendido, en el orden pedido (crit. 2) y hasta
 * el límite elegido (crit. 3). Si se vendieron más productos de los que se muestran, lo indica.
 */
export function TablaProductosMasVendidos({
  reporte,
  exportacion,
}: {
  reporte: ReporteProductosMasVendidos
  exportacion?: Exportacion
}) {
  const recortado = reporte.productosVendidos > reporte.filas.length
  return (
    <section aria-label="Productos más vendidos">
      <EncabezadoDeReporte encabezado={reporte.encabezado} exportacion={exportacion} />
      {reporte.filas.length === 0 ? (
        <p className="tarjeta">No se vendieron productos con estos filtros.</p>
      ) : (
        <>
          <p>
            {recortado
              ? `Se muestran los ${reporte.filas.length} primeros de ${reporte.productosVendidos} productos vendidos.`
              : `${reporte.productosVendidos === 1 ? 'Se vendió 1 producto' : `Se vendieron ${reporte.productosVendidos} productos`} en el período.`}
          </p>
          <div className="tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr>
                  <th>N.º</th>
                  <th>Código</th>
                  <th>Descripción</th>
                  <th aria-sort={reporte.ordenarPor === 'CANTIDAD' ? 'descending' : undefined}>Cantidad vendida</th>
                  <th aria-sort={reporte.ordenarPor === 'MONTO' ? 'descending' : undefined}>Monto vendido</th>
                </tr>
              </thead>
              <tbody>
                {reporte.filas.map((fila) => (
                  <tr key={fila.productoId}>
                    <td>{fila.posicion}</td>
                    <td>{fila.codigo}</td>
                    <td>{fila.descripcion}</td>
                    <td>{fila.cantidad}</td>
                    <td>{formatoQuetzales(fila.monto)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row" colSpan={3}>
                    {recortado ? `Total de los ${reporte.filas.length} mostrados` : 'Total'}
                  </th>
                  <td>{reporte.totales.cantidad}</td>
                  <td>{formatoQuetzales(reporte.totales.monto)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
      <p>
        <small>
          Líneas vigentes de las ventas finalizadas en el período, sin las de facturas anuladas. El monto incluye IVA.
        </small>
      </p>
    </section>
  )
}
