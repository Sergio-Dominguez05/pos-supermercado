import { useMutation, useQueryClient } from '@tanstack/react-query'
import { abrirTurno, CLAVE_TURNO_ACTUAL } from '../../api/turnos'
import { useSesion } from '../login/sesion'
import { pedidoAbrirTurno, turnoAbierto, turnoDeOtroUsuario } from './textos'
import { operaCaja as usuarioOperaCaja, useTurnoActual } from './turno'

/**
 * HU-09.01: estado del turno de la caja de este equipo. Si la caja está cerrada pide abrir el turno (crit. 1).
 * No muestra nada si el usuario no opera caja o si el equipo no está vinculado a una.
 */
export function EstadoTurno() {
  const { data: usuario } = useSesion()
  const operaCaja = usuarioOperaCaja(usuario)
  const { data: turno, isPending, error } = useTurnoActual(usuario)
  const clienteConsultas = useQueryClient()
  const apertura = useMutation({
    mutationFn: abrirTurno,
    onSuccess: (abierto) => clienteConsultas.setQueryData(CLAVE_TURNO_ACTUAL, abierto),
  })

  if (!usuario || !operaCaja) return null
  if (isPending) return <p className="tarjeta">Consultando el turno de la caja…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>

  if (turno?.propio) {
    return (
      <p className="tarjeta aviso" role="status">
        {turnoAbierto(turno)}
      </p>
    )
  }
  if (turno) {
    return (
      <p className="tarjeta error" role="alert">
        {turnoDeOtroUsuario(turno)}
      </p>
    )
  }
  return (
    <section className="tarjeta formulario" aria-label="Abrir turno">
      <h2>Abrir turno</h2>
      <p>{pedidoAbrirTurno(usuario.cajaNombre ?? '')}</p>
      {apertura.error && (
        <p className="error" role="alert">
          {apertura.error.message}
        </p>
      )}
      <button type="button" disabled={apertura.isPending} onClick={() => apertura.mutate()}>
        {apertura.isPending ? 'Abriendo…' : 'Abrir turno'}
      </button>
    </section>
  )
}
