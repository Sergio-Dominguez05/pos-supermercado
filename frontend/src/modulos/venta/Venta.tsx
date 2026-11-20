import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { ErrorDeApi } from '../../api/cliente'
import { CLAVE_COBRO, type FacturaDeVenta } from '../../api/pagos'
import {
  cancelarVenta,
  CLAVE_VENTA_EN_CURSO,
  CLAVE_VENTAS_SUSPENDIDAS,
  iniciarVenta,
  listarSuspendidas,
  pasarAlCobro,
  type Venta as DatosVenta,
} from '../../api/ventas'
import { ACCIONES_PENDIENTES, ATAJOS, avisoPendiente } from '../../atajos/atajos'
import { useAtajos, type Manejador, type Manejadores } from '../../atajos/useAtajos'
import { EstadoTurno } from '../caja/EstadoTurno'
import { puedeVender, useTurnoActual } from '../caja/turno'
import { formatoQuetzales } from '../catalogo/textos'
import { ClienteDeLaVenta } from '../clientes/ClienteDeLaVenta'
import { useSesion } from '../login/sesion'
import { avisoSonoro } from '../catalogo/sonido'
import { AyudaAtajos } from './AyudaAtajos'
import { BusquedaEnVenta } from './BusquedaEnVenta'
import { CantidadLinea } from './CantidadLinea'
import { QuitarLinea } from './QuitarLinea'
import { SuspenderVenta } from './SuspenderVenta'
import { VentasSuspendidas } from './VentasSuspendidas'
import { EscanerVenta } from './EscanerVenta'
import { reimprimirUltima } from '../../api/facturas'
import { AvisoContingenciaVenta } from '../facturas/Contingencia'
import { CorregirFactura } from '../facturas/CorregirFactura'
import { avisoFactura, avisoVentaFinalizada } from './cobro'
import { PasoDeCobro } from './PasoDeCobro'
import { type AccionDeLaVenta, lineaSeleccionada, moverSeleccion, supresionEsParaLaLinea } from './teclado'
import {
  avisoVentaRetomada,
  avisoVentaSuspendida,
  confirmacionCancelarVenta,
  formatoDesglose,
  rotuloSuspendidas,
} from './textos'

/** Lleva el foco a un campo de la pantalla de venta por su nombre (`codigo` o `descripcion`). */
const enfocar = (campo: 'codigo' | 'descripcion' | 'nit') =>
  document.querySelector<HTMLInputElement>(`main input[name="${campo}"]`)?.focus()

/** HU-06.15 crit. 2: el servidor rechazó el cobro porque la venta no tenía productos. */
const esVentaVacia = (error: Error) => error instanceof ErrorDeApi && error.codigo === 'VENTA_VACIA'

/** El control de una acción en la fila de la línea (`cantidad` o `quitar`). */
const controlDeLinea = (lineaId: number, control: string) =>
  document.querySelector<HTMLElement>(`main tr[data-linea="${lineaId}"] [data-control="${control}"]`)

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
  // HU-06.13 crit. 1 y 3: las ventas en espera de la caja propia.
  const { data: suspendidas = [] } = useQuery({
    queryKey: CLAVE_VENTAS_SUSPENDIDAS,
    queryFn: listarSuspendidas,
    enabled: habilitada,
  })
  const [listaVisible, setListaVisible] = useState(false)

  // HU-06.17: la línea sobre la que actúan F4 y Supr. Se ajusta en el render cada vez que llega otra venta.
  const [seleccion, setSeleccion] = useState<{ venta?: DatosVenta; id?: number }>({})
  if (venta !== seleccion.venta) {
    setSeleccion({ venta, id: venta && lineaSeleccionada(seleccion.venta, venta, seleccion.id) })
  }
  const seleccionada = seleccion.id
  const [ayudaVisible, setAyudaVisible] = useState(false)
  const [aviso, setAviso] = useState<string>()
  // HU-08.06: la factura de la última venta que rechazó el certificador, para corregirla sin volver a cobrar.
  const [rechazada, setRechazada] = useState<{ ventaId: number; numero: number; factura: FacturaDeVenta }>()

  // HU-06.15: "Cobrar" (F12) pide al servidor validar la venta (crit. 2 y 3) y, si puede cobrarse, la pantalla pasa al
  // cobro. La venta sigue en curso: al regresar se pueden agregar o quitar productos (crit. 4).
  const clienteConsultas = useQueryClient()
  const [enCobro, setEnCobro] = useState(false)
  const cobro = useMutation({
    mutationFn: () => pasarAlCobro(),
    onSuccess: (lista) => {
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, lista)
      void clienteConsultas.invalidateQueries({ queryKey: CLAVE_COBRO })
      setAviso(undefined)
      setEnCobro(true)
    },
    onError: () => avisoSonoro(),
  })
  const cobrar = cobro.mutate
  // HU-08.12 crit. 1: F9 reimprime la última factura de esta caja, como copia.
  const reimpresion = useMutation({
    mutationFn: () => reimprimirUltima(),
    onSuccess: (resultado) => setAviso(resultado.mensaje ?? 'Se reimprimió la última factura.'),
    onError: (error) => setAviso(error.message),
  })
  const reimprimir = reimpresion.mutate
  // Si la venta deja de tener productos (por ejemplo, se canceló desde otra pestaña), no hay nada que cobrar.
  if (enCobro && !venta?.lineas.length) setEnCobro(false)
  const regresarALaVenta = () => {
    setEnCobro(false)
    cobro.reset()
    enfocar('codigo')
  }

  // HU-06.17 crit. 1: cada atajo del mapa central (Architecture.md §7.6) con su acción en la venta. Los de acciones
  // que todavía no existen avisan con qué historia llegan. HU-06.03 crit. 4: F2 abre la búsqueda por descripción.
  const manejadores = useMemo<Manejadores>(() => {
    const disponibles: Record<AccionDeLaVenta, Manejador> = {
      ayuda: () => setAyudaVisible((visible) => !visible),
      buscarProducto: () => enfocar('descripcion'),
      cambiarCantidad: () => {
        const campo = seleccionada === undefined ? null : controlDeLinea(seleccionada, 'cantidad')
        if (campo instanceof HTMLInputElement) {
          campo.focus()
          campo.select()
        }
      },
      eliminarLinea: (evento) => {
        if (!supresionEsParaLaLinea(evento.target)) return false
        if (seleccionada !== undefined) controlDeLinea(seleccionada, 'quitar')?.click()
      },
      // EP-05: F6 lleva al NIT del cliente (HU-05.02); "CF" vuelve a Consumidor Final (HU-05.01).
      asignarCliente: () => enfocar('nit'),
      suspenderVenta: () => document.querySelector<HTMLElement>('main [data-control="suspender-venta"]')?.click(),
      retomarVenta: () => setListaVisible((visible) => !visible),
      cancelarVenta: () => document.querySelector<HTMLElement>('main [data-control="cancelar-venta"]')?.click(),
      cobrar: () => cobrar(),
      reimprimirUltima: () => reimprimir(),
    }
    const pendientes: Manejadores = Object.fromEntries(
      ATAJOS.filter((atajo) => ACCIONES_PENDIENTES[atajo.accion]).map((atajo) => [
        atajo.accion,
        () => setAviso(avisoPendiente(atajo)),
      ]),
    )
    // En el cobro solo queda la ayuda; Escape regresa a la venta (PasoDeCobro).
    return habilitada && !enCobro ? { ...pendientes, ...disponibles } : { ayuda: disponibles.ayuda }
  }, [habilitada, seleccionada, enCobro, cobrar, reimprimir])
  useAtajos(manejadores)

  // HU-06.17: en el campo del código, las flechas arriba y abajo eligen la línea (el campo es de una sola línea).
  useEffect(() => {
    const alPresionar = (evento: KeyboardEvent) => {
      const paso = evento.key === 'ArrowUp' ? -1 : evento.key === 'ArrowDown' ? 1 : 0
      const enElCodigo = evento.target instanceof HTMLInputElement && evento.target.name === 'codigo'
      if (!paso || !enElCodigo || !venta?.lineas.length) return
      evento.preventDefault()
      setSeleccion((actual) => ({ ...actual, id: moverSeleccion(venta, actual.id, paso) }))
    }
    window.addEventListener('keydown', alPresionar)
    return () => window.removeEventListener('keydown', alPresionar)
  }, [venta])
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Venta</h1>
      <p className="subtitulo">
        {usuario.nombreCompleto} · {usuario.rolNombre} · <kbd>F1</kbd> Atajos de teclado
      </p>

      {/* HU-06.17 crit. 2: los atajos, en una ayuda rápida que se abre y se cierra con F1. */}
      {ayudaVisible && <AyudaAtajos onCerrar={() => setAyudaVisible(false)} />}

      {/* HU-06.13: la lista de ventas en espera, con F8 o con su botón. */}
      {habilitada && listaVisible && (
        <VentasSuspendidas
          suspendidas={suspendidas}
          onCerrar={() => {
            setListaVisible(false)
            enfocar('codigo')
          }}
          onRetomada={(retomada) => {
            setListaVisible(false)
            setAviso(avisoVentaRetomada(retomada))
            enfocar('codigo')
          }}
        />
      )}

      {/* HU-09.01 crit. 1: en una caja cerrada se pide abrir el turno. */}
      <EstadoTurno />

      {/* HU-08.07 crit. 3: el cajero sabe que se está operando en contingencia. */}
      {habilitada && <AvisoContingenciaVenta />}

      {/* HU-09.02 crit. 1 y HU-06.01 crit. 2: con la caja cerrada la pantalla de venta está deshabilitada. */}
      <section className="tarjeta formulario" aria-label="Venta">
        {habilitada && venta && <h2>Venta N.º {venta.numero}</h2>}
        {habilitada && suspendidas.length > 0 && (
          <p>
            <button type="button" onClick={() => setListaVisible((visible) => !visible)}>
              {rotuloSuspendidas(suspendidas.length)} <kbd>F8</kbd>
            </button>
          </p>
        )}
        {aviso && (
          <p className="pendiente" role="status">
            {aviso}
          </p>
        )}
        {/* El aviso de venta vacía deja de tener sentido en cuanto la venta tiene productos. */}
        {cobro.error && !(esVentaVacia(cobro.error) && venta?.lineas.length) && (
          <p className="error" role="alert">
            {cobro.error.message}
          </p>
        )}
        {/* HU-08.06: si el certificador rechazó la factura de la venta recién cobrada, se corrige aquí. */}
        {rechazada && (
          <CorregirFactura
            key={rechazada.ventaId}
            ventaId={rechazada.ventaId}
            numero={rechazada.numero}
            factura={rechazada.factura}
            onTerminado={(resultado) => {
              if (resultado.estado === 'RECHAZADA') {
                setRechazada({ ...rechazada, factura: resultado })
                return
              }
              setRechazada(undefined)
              setAviso(`Venta N.º ${rechazada.numero}: ${avisoFactura(resultado)}`)
            }}
          />
        )}
        {/* EP-05: el cliente de la venta, también en el cobro (se puede cambiar hasta finalizar). */}
        {habilitada && venta && <ClienteDeLaVenta key={venta.id} ventaId={venta.id} />}
        {habilitada && venta && enCobro ? (
          <>
            <PasoDeCobro
              venta={venta}
              onRegresar={regresarALaVenta}
              onFinalizada={(finalizada) => {
                // HU-07.08 crit. 5: el aviso dice qué venta se finalizó y la pantalla queda en la siguiente.
                setAviso(avisoVentaFinalizada(finalizada))
                setRechazada(
                  finalizada.factura.estado === 'RECHAZADA'
                    ? { ventaId: finalizada.ventaId, numero: finalizada.numero, factura: finalizada.factura }
                    : undefined,
                )
                regresarALaVenta()
              }}
            />
            <DetalleVenta venta={venta} />
          </>
        ) : (
          <>
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
            {!habilitada && (
              <p className="pendiente">La venta está deshabilitada: abra su turno en una caja para vender.</p>
            )}
            {habilitada && error && (
              <p className="error" role="alert">
                {error.message}
              </p>
            )}
            {habilitada && venta && (
              <DetalleVenta
                venta={venta}
                editable
                seleccionada={seleccionada}
                onSeleccionar={(id) => setSeleccion((actual) => ({ ...actual, id }))}
                onTerminado={() => enfocar('codigo')}
              />
            )}
            {habilitada && venta && venta.lineas.length > 0 && (
              <div className="acciones">
                {/* HU-06.15 crit. 1 y 2: con al menos un producto se puede pasar al cobro. */}
                <button type="button" data-control="cobrar" disabled={cobro.isPending} onClick={() => cobrar()}>
                  {cobro.isPending ? 'Validando…' : 'Cobrar'} <kbd>F12</kbd>
                </button>
                {/* HU-06.12: suspender avisa qué venta quedó en espera; el aviso queda aunque llegue la venta nueva. */}
                <SuspenderVenta venta={venta} onSuspendida={(suspendida) => setAviso(avisoVentaSuspendida(suspendida))} />
                <CancelarVenta venta={venta} />
              </div>
            )}
          </>
        )}
      </section>
    </main>
  )
}

/**
 * Líneas de la venta y sus totales, tal como los calcula el servidor (HU-04.04, HU-06.08). Con `editable`, la
 * cantidad de cada línea se puede cambiar (HU-06.04 crit. 2) y la línea se puede quitar (HU-06.07).
 */
export function DetalleVenta({
  venta,
  editable = false,
  seleccionada,
  onSeleccionar,
  onTerminado,
}: {
  venta: DatosVenta
  editable?: boolean
  /** HU-06.17: la línea sobre la que actúan F4 y Supr; también se elige con un clic. */
  seleccionada?: number
  onSeleccionar?: (lineaId: number) => void
  onTerminado?: () => void
}) {
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
                {editable && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {venta.lineas.map((linea) => (
                <tr
                  key={linea.id}
                  data-linea={linea.id}
                  className={editable && linea.id === seleccionada ? 'seleccionada' : undefined}
                  aria-selected={editable ? linea.id === seleccionada : undefined}
                  onClick={() => onSeleccionar?.(linea.id)}
                >
                  <td>{linea.codigoBarras}</td>
                  <td>{linea.descripcion}</td>
                  <td>
                    {editable ? (
                      // La clave incluye la cantidad: si cambia (otro escaneo), el campo muestra la nueva.
                      <CantidadLinea key={`${linea.id}-${linea.cantidad}`} linea={linea} onTerminado={onTerminado} />
                    ) : (
                      linea.cantidad
                    )}
                  </td>
                  <td>{formatoQuetzales(linea.precioUnitario)}</td>
                  <td>{formatoQuetzales(linea.subtotalConIva)}</td>
                  {editable && (
                    <td>
                      <QuitarLinea linea={linea} onTerminado={onTerminado} />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {venta.lineas.length > 0 && <DesgloseIva venta={venta} />}
      {/* HU-04.04 crit. 1 y HU-06.08: artículos, Subtotal, IVA y TOTAL, en ese orden, siempre visibles (también con la
          venta vacía) y actualizados con cada producto, porque cada respuesta del servidor trae la venta completa. */}
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
    <>
      <button
        type="button"
        data-control="cancelar-venta"
        disabled={cancelacion.isPending}
        onClick={() => {
          if (window.confirm(confirmacionCancelarVenta(venta))) cancelacion.mutate()
        }}
      >
        {cancelacion.isPending ? 'Cancelando…' : 'Cancelar venta'} <kbd>F10</kbd>
      </button>
      {cancelacion.error && (
        <p className="error" role="alert">
          {cancelacion.error.message}
        </p>
      )}
    </>
  )
}
