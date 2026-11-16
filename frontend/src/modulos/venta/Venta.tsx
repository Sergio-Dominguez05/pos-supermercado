import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { cancelarVenta, CLAVE_VENTA_EN_CURSO, iniciarVenta, type Venta as DatosVenta } from '../../api/ventas'
import { EstadoTurno } from '../caja/EstadoTurno'
import { puedeVender, useTurnoActual } from '../caja/turno'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { BusquedaEnVenta } from './BusquedaEnVenta'
import { EscanerVenta } from './EscanerVenta'
import { confirmacionCancelarVenta, formatoDesglose } from './textos'

/** Lleva el foco a un campo de la pantalla de venta por su nombre (`codigo` o `descripcion`). */
const enfocar = (campo: 'codigo' | 'descripcion') =>
  document.querySelector<HTMLInputElement>(`main input[name="${campo}"]`)?.focus()

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

  // HU-06.03 crit. 4: todo con el teclado. F2 abre la búsqueda por descripción (Architecture.md §7.6); Escape en la
  // búsqueda y elegir un producto devuelven el foco al campo del código, listo para el siguiente escaneo.
  useEffect(() => {
    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === 'F2') {
        evento.preventDefault()
        enfocar('descripcion')
      }
    }
    window.addEventListener('keydown', alPresionar)
    return () => window.removeEventListener('keydown', alPresionar)
  }, [])
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
          <EscanerVenta key={venta?.id} onBuscarPorDescripcion={() => enfocar('descripcion')} />
          {/* HU-06.03 crit. 2: buscar por descripción, elegir y agregar. */}
          <section
            aria-label="Buscar por descripción"
            onKeyDown={(evento) => {
              if (evento.key === 'Escape') enfocar('codigo')
            }}
          >
            <h3>
              Buscar por descripción <kbd>F2</kbd>
            </h3>
            <BusquedaEnVenta key={venta?.id} onTerminado={() => enfocar('codigo')} />
          </section>
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
      {venta.lineas.length > 0 && <DesgloseIva venta={venta} />}
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
 * HU-04.03 crit. 3: el desglose de cada línea (subtotal con IVA, base imponible e IVA) con los 6 decimales que guarda
 * la venta, sin redondear (crit. 2). Queda plegado para no distraer al cajero; se abre con el teclado (Enter sobre
 * el resumen) o con el ratón.
 */
export function DesgloseIva({ venta }: { venta: DatosVenta }) {
  return (
    <details className="desglose">
      <summary>Desglose de IVA por línea</summary>
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Descripción</th>
              <th>Cantidad</th>
              <th>Precio unitario</th>
              <th>Subtotal con IVA</th>
              <th>Base imponible</th>
              <th>IVA</th>
            </tr>
          </thead>
          <tbody>
            {venta.lineas.map((linea) => (
              <tr key={linea.id}>
                <td>{linea.descripcion}</td>
                <td>{linea.cantidad}</td>
                <td>{formatoQuetzales(linea.precioUnitario)}</td>
                <td>{formatoDesglose(linea.subtotalConIva)}</td>
                <td>{formatoDesglose(linea.baseImponible)}</td>
                <td>{formatoDesglose(linea.iva)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
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
