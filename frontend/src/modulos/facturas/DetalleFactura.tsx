import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import {
  claveDetalleFactura,
  obtenerDetalleFactura,
  reimprimirFactura,
  type DetalleFactura,
  type FacturaEmitida,
} from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { nitConGuion } from '../clientes/textos'
import { useSesion } from '../login/sesion'
import { EstadoFactura } from './AnularFacturas'
import { ESTADOS_DE_FACTURA, numeroDeFactura } from './textos'

const fecha = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Guatemala' })

const TARJETAS = { DEBITO: 'Débito', CREDITO: 'Crédito' } as const

/** HU-08.14: el detalle de una factura, para el Supervisor y el Administrador. */
export function PaginaDetalleFactura() {
  const { data: usuario } = useSesion()
  const { facturaId } = useParams()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Detalle de factura</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/facturas/historial">Historial de facturas</Link> · Detalle
      </p>
      {usuario.permisos.includes('FACTURA_ANULAR') ? (
        <CargaDetalle facturaId={Number(facturaId)} permisos={usuario.permisos} />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar el detalle de las facturas.</p>
      )}
    </main>
  )
}

function CargaDetalle({ facturaId, permisos }: { facturaId: number; permisos: readonly string[] }) {
  const { data, error, isPending } = useQuery({
    queryKey: claveDetalleFactura(facturaId),
    queryFn: () => obtenerDetalleFactura(facturaId),
  })
  if (isPending) return <p className="tarjeta">Cargando la factura…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <VistaDetalleFactura factura={data} permisos={permisos} />
}

/** Lo que el componente de anulación de HU-08.10 necesita saber de la factura. */
const comoEmitida = (factura: DetalleFactura): FacturaEmitida => ({
  facturaId: factura.facturaId,
  ventaId: factura.ventaId,
  ventaNumero: factura.ventaNumero,
  turnoId: 0,
  turnoAbierto: factura.turnoAbierto,
  cajaNombre: factura.caja,
  estado: factura.estado === 'ANULADA' ? 'ANULADA' : 'CERTIFICADA',
  serie: factura.certificacion.serie ?? '',
  numero: factura.certificacion.numero ?? '',
  autorizacion: factura.certificacion.autorizacion ?? '',
  fechaCertificacion: factura.certificacion.certificadaEn ?? '',
  emitidaEn: factura.emitidaEn,
  total: factura.total,
  receptorId: factura.receptor.id,
  receptorNombre: factura.receptor.nombre,
  motivoAnulacion: null,
  anuladaEn: null,
})

/** Crit. 1 a 3: los datos de la factura, su historial y lo que se puede hacer con ella según los permisos. */
export function VistaDetalleFactura({ factura, permisos }: { factura: DetalleFactura; permisos: readonly string[] }) {
  const { emisor, receptor, certificacion } = factura
  return (
    <>
      <section className="tarjeta" aria-label="Factura">
        <h2>
          {numeroDeFactura({ ...certificacion })} · {ESTADOS_DE_FACTURA[factura.estado] ?? factura.estado}
        </h2>
        <dl className="resumen">
          <dt>Venta</dt>
          <dd>N.º {factura.ventaNumero}</dd>
          <dt>Emitida</dt>
          <dd>{fecha(factura.emitidaEn)}</dd>
          <dt>Caja</dt>
          <dd>{factura.caja}</dd>
          <dt>Cajero</dt>
          <dd>{factura.cajero}</dd>
        </dl>
        <Acciones factura={factura} permisos={permisos} />
      </section>

      <div className="columnas">
        <section className="tarjeta" aria-label="Emisor">
          <h3>Emisor</h3>
          <p>
            <strong>{emisor.nombreComercial ?? '—'}</strong>
            <br />
            {emisor.razonSocial} · NIT {emisor.nit ? nitConGuion(emisor.nit) : '—'}
            <br />
            {emisor.direccionFiscal}
            <br />
            Establecimiento {emisor.codigoEstablecimiento} · {emisor.establecimiento}
            {emisor.direccionEstablecimiento && <> · {emisor.direccionEstablecimiento}</>}
          </p>
        </section>
        <section className="tarjeta" aria-label="Receptor">
          <h3>Cliente</h3>
          <p>
            <strong>{receptor.nombre}</strong>
            <br />
            {receptor.tipo === 'CF' ? 'Consumidor Final' : `${receptor.tipo} ${nitConGuion(receptor.id)}`}
            {receptor.direccion && (
              <>
                <br />
                {receptor.direccion}
              </>
            )}
            {receptor.correo && (
              <>
                <br />
                {receptor.correo}
              </>
            )}
          </p>
        </section>
        <section className="tarjeta" aria-label="Certificación">
          <h3>Certificación</h3>
          <dl className="resumen">
            <dt>Serie y número</dt>
            <dd>{certificacion.serie ? `${certificacion.serie}-${certificacion.numero}` : '—'}</dd>
            <dt>Autorización</dt>
            <dd>
              <small>{certificacion.autorizacion ? certificacion.autorizacion.toUpperCase() : '—'}</small>
            </dd>
            <dt>Certificada</dt>
            <dd>{certificacion.certificadaEn ? fecha(certificacion.certificadaEn) : '—'}</dd>
            {certificacion.numeroAcceso && (
              <>
                <dt>Número de acceso</dt>
                <dd>{certificacion.numeroAcceso}</dd>
              </>
            )}
          </dl>
        </section>
      </div>

      <section aria-label="Productos">
        <h3>Productos</h3>
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Descripción</th>
                <th>Cantidad</th>
                <th>Precio</th>
                <th>Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {factura.lineas.map((linea, indice) => (
                <tr key={indice}>
                  <td>{linea.descripcion}</td>
                  <td>{linea.cantidad}</td>
                  <td>{formatoQuetzales(linea.precioUnitario)}</td>
                  <td>{formatoQuetzales(linea.subtotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row" colSpan={3}>
                  Subtotal
                </th>
                <td>{formatoQuetzales(factura.subtotal)}</td>
              </tr>
              <tr>
                <th scope="row" colSpan={3}>
                  IVA
                </th>
                <td>{formatoQuetzales(factura.iva)}</td>
              </tr>
              <tr>
                <th scope="row" colSpan={3}>
                  TOTAL
                </th>
                <td>{formatoQuetzales(factura.total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <section aria-label="Pagos">
        <h3>Pagos</h3>
        {factura.pagos.length === 0 ? (
          <p className="tarjeta">La venta no tiene pagos registrados.</p>
        ) : (
          <ul className="tarjeta">
            {factura.pagos.map((pago, indice) => (
              <li key={indice}>
                {TARJETAS[pago.tipoTarjeta]} {pago.ultimos4 ? `•••• ${pago.ultimos4}` : ''} · autorización{' '}
                {pago.autorizacion} · {formatoQuetzales(pago.monto)}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-label="Historial de la factura">
        <h3>Historial</h3>
        <ol className="tarjeta historial-factura">
          {factura.historial.map((evento, indice) => (
            <li key={indice}>
              <time dateTime={evento.momento}>{fecha(evento.momento)}</time> · {evento.descripcion}
              {evento.usuario && <small> · {evento.usuario}</small>}
            </li>
          ))}
        </ol>
      </section>
    </>
  )
}

/**
 * Crit. 3: reimprimir (permiso de reimprimir, factura certificada) y anular (permiso de anular, con las reglas de
 * HU-08.10). El envío por correo llega con HU-08.15.
 */
function Acciones({ factura, permisos }: { factura: DetalleFactura; permisos: readonly string[] }) {
  const clienteConsultas = useQueryClient()
  const refrescar = () => clienteConsultas.invalidateQueries({ queryKey: ['facturas'] })
  const reimpresion = useMutation({
    mutationFn: () => reimprimirFactura(factura.facturaId),
    onSettled: refrescar,
  })
  const certificada = factura.estado === 'CERTIFICADA'
  const puedeReimprimir = certificada && permisos.includes('FACTURA_REIMPRIMIR')
  const puedeAnular = certificada && permisos.includes('FACTURA_ANULAR')
  if (!puedeReimprimir && !puedeAnular) return null

  return (
    <div className="acciones-factura">
      {puedeReimprimir && (
        <div>
          <button type="button" disabled={reimpresion.isPending} onClick={() => reimpresion.mutate()}>
            {reimpresion.isPending ? 'Imprimiendo…' : 'Reimprimir'}
          </button>
          {reimpresion.data?.mensaje && (
            <p className="exito" role="status">
              {reimpresion.data.mensaje}
            </p>
          )}
          {reimpresion.error && (
            <p className="error" role="alert">
              {reimpresion.error.message}
            </p>
          )}
        </div>
      )}
      {puedeAnular && (
        <div>
          <EstadoFactura factura={comoEmitida(factura)} onAnulada={() => void refrescar()} />
        </div>
      )}
    </div>
  )
}
