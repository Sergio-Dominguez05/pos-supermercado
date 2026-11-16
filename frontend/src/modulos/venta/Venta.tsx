import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelarVenta, CLAVE_VENTA_EN_CURSO, iniciarVenta, type Venta as DatosVenta } from '../../api/ventas'
import { EstadoTurno } from '../caja/EstadoTurno'
import { puedeVender, useTurnoActual } from '../caja/turno'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { EscanerVenta } from './EscanerVenta'
import { confirmacionCancelarVenta } from './textos'

/** Destino del Cajero al iniciar sesión (HU-01.02) y pantalla de venta (EP-06). */
export function Venta() {
  const { data: usuario } = useSesion()
  const { data: turno } = useTurnoActual(usuario)
  const habilitada = puedeVender(usuario, turno)
  // HU-06.01 crit. 1: con el turno abierto la venta queda lista. Iniciar devuelve la venta en curso si ya existe,
  // así que recargar la página o volver a entrar retoma la misma venta.
  const { data: venta, error } = useQuery({
    queryKey: CLAVE_VENTA_EN_CURSO,
    queryFn: iniciarVenta,
    enabled: habilitada,
  })
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Venta</h1>
      <p className="subtitulo">
        {usuario.nombreCompleto} · {usuario.rolNombre}
      </p>

      {/* HU-09.01 crit. 1: en una caja cerrada se pide abrir el turno. */}
      <EstadoTurno />

      {/* HU-09.02 crit. 1 y HU-06.01 crit. 2: con la caja cerrada la pantalla de venta está deshabilitada. */}
      <section className="tarjeta formulario" aria-label="Venta">
        {habilitada && venta && <h2>Venta N.º {venta.numero}</h2>}
        <fieldset disabled={!habilitada || !venta}>
          {/* HU-06.02: el lector de código de barras (o el código escrito a mano) agrega el producto. */}
          {/* Se reinicia con cada venta, para que no quede el aviso del último escaneo de la anterior. */}
          <EscanerVenta key={venta?.id} />
        </fieldset>
        {!habilitada && <p className="pendiente">La venta está deshabilitada: abra su turno en una caja para vender.</p>}
        {habilitada && error && (
          <p className="error" role="alert">
            {error.message}
          </p>
        )}
        {habilitada && venta && <DetalleVenta venta={venta} />}
        {habilitada && venta && <CancelarVenta venta={venta} />}
      </section>
    </main>
  )
}

/** Líneas de la venta y sus totales, tal como los calcula el servidor (HU-04.04, HU-06.08). */
export function DetalleVenta({ venta }: { venta: DatosVenta }) {
  return (
    <>
      {venta.lineas.length === 0 ? (
        <p>Lista para recibir productos: escanee o escriba un código.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Código</th>
                <th>Descripción</th>
                <th>Cantidad</th>
                <th>Precio</th>
                <th>Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {venta.lineas.map((linea) => (
                <tr key={linea.id}>
                  <td>{linea.codigoBarras}</td>
                  <td>{linea.descripcion}</td>
                  <td>{linea.cantidad}</td>
                  <td>{formatoQuetzales(linea.precioUnitario)}</td>
                  <td>{formatoQuetzales(linea.subtotalConIva)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {/* HU-04.04 crit. 1: Subtotal, IVA y TOTAL, en ese orden. */}
      <dl className="totales" aria-label="Totales">
        <dt>Artículos</dt>
        <dd>{venta.articulos}</dd>
        <dt>Subtotal</dt>
        <dd>{formatoQuetzales(venta.subtotal)}</dd>
        <dt>IVA</dt>
        <dd>{formatoQuetzales(venta.iva)}</dd>
        <dt>TOTAL</dt>
        <dd className="total">{formatoQuetzales(venta.total)}</dd>
      </dl>
    </>
  )
}

/**
 * HU-06.14: cancelar la venta en curso, después de confirmarlo (crit. 1) y sin autorización de un supervisor
 * (crit. 2). Al cancelarla, la pantalla inicia la siguiente venta (HU-06.01 crit. 3). Una venta sin productos no
 * tiene nada que cancelar.
 */
export function CancelarVenta({ venta }: { venta: DatosVenta }) {
  const clienteConsultas = useQueryClient()
  const cancelacion = useMutation({
    mutationFn: cancelarVenta,
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: CLAVE_VENTA_EN_CURSO }),
  })
  if (venta.lineas.length === 0) return null

  return (
    <div className="acciones">
      <button
        type="button"
        disabled={cancelacion.isPending}
        onClick={() => {
          if (window.confirm(confirmacionCancelarVenta(venta))) cancelacion.mutate()
        }}
      >
        {cancelacion.isPending ? 'Cancelando…' : 'Cancelar venta'}
      </button>
      {cancelacion.error && (
        <p className="error" role="alert">
          {cancelacion.error.message}
        </p>
      )}
    </div>
  )
}
