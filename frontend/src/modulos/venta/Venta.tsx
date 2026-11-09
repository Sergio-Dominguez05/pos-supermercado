import { EstadoTurno } from '../caja/EstadoTurno'
import { puedeVender, useTurnoActual } from '../caja/turno'
import { useSesion } from '../login/sesion'

/** Destino del Cajero al iniciar sesión (HU-01.02). La pantalla de venta llega con HU-06.01. */
export function Venta() {
  const { data: usuario } = useSesion()
  const { data: turno } = useTurnoActual(usuario)
  if (!usuario) return null
  const habilitada = puedeVender(usuario, turno)

  return (
    <main className="pagina">
      <h1>Venta</h1>
      <p className="subtitulo">
        {usuario.nombreCompleto} · {usuario.rolNombre}
      </p>

      {/* HU-09.01 crit. 1: en una caja cerrada se pide abrir el turno. */}
      <EstadoTurno />

      {/* HU-09.02 crit. 1: con la caja cerrada la pantalla de venta está deshabilitada. */}
      <section className="tarjeta formulario" aria-label="Venta">
        <fieldset disabled={!habilitada}>
          <label>
            Código de producto
            <input name="codigo" autoComplete="off" placeholder="Escanee o escriba el código" />
          </label>
        </fieldset>
        <p className={habilitada ? undefined : 'pendiente'}>
          {habilitada
            ? 'La pantalla de venta todavía no está disponible (HU-06.01).'
            : 'La venta está deshabilitada: abra su turno en una caja para vender.'}
        </p>
      </section>
    </main>
  )
}
