import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  cerrarTurnoDeOtro,
  CLAVE_TURNOS_POR_CERRAR,
  listarTurnosPorCerrar,
  obtenerResumenDeTurno,
  type CierreHecho,
  type TurnoAbierto,
} from '../../api/turnos'
import { useSesion } from '../login/sesion'
import { CierreTerminado, FormularioCierre } from './CierreCaja'

const fecha = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Guatemala' })

/** HU-09.12: el supervisor cierra la caja de otro usuario (por ejemplo, si el cajero se retiró sin cerrarla). */
export function PaginaTurnosAbiertos() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Cerrar la caja de otro usuario</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Turnos abiertos
      </p>
      {usuario.permisos.includes('CAJA_CERRAR_OTRA') ? (
        <CierreDeOtro />
      ) : (
        <p className="tarjeta error">No tiene permiso para cerrar la caja de otro usuario.</p>
      )}
    </main>
  )
}

function CierreDeOtro() {
  const clienteConsultas = useQueryClient()
  const [elegido, setElegido] = useState<TurnoAbierto>()
  const [hecho, setHecho] = useState<CierreHecho>()
  const abiertos = useQuery({ queryKey: CLAVE_TURNOS_POR_CERRAR, queryFn: listarTurnosPorCerrar })

  if (hecho)
    return (
      <>
        <CierreTerminado hecho={hecho} />
        <button
          type="button"
          onClick={() => {
            setHecho(undefined)
            setElegido(undefined)
          }}
        >
          Volver a los turnos abiertos
        </button>
      </>
    )
  if (elegido)
    return (
      <>
        <ResumenDeOtro
          turno={elegido}
          onCerrada={(cierre) => {
            void clienteConsultas.invalidateQueries({ queryKey: CLAVE_TURNOS_POR_CERRAR })
            setHecho(cierre)
          }}
        />
        <button type="button" onClick={() => setElegido(undefined)}>
          Elegir otro turno
        </button>
      </>
    )
  if (abiertos.isPending) return <p className="tarjeta">Buscando turnos abiertos…</p>
  if (abiertos.error) return <p className="tarjeta error">{abiertos.error.message}</p>
  return <ListaTurnosAbiertos turnos={abiertos.data} onElegir={setElegido} />
}

/** Crit. 1: los turnos abiertos de los demás, para elegir cuál cerrar. */
export function ListaTurnosAbiertos({
  turnos,
  onElegir,
}: {
  turnos: TurnoAbierto[]
  onElegir: (turno: TurnoAbierto) => void
}) {
  if (turnos.length === 0) return <p className="tarjeta">No hay turnos abiertos de otros usuarios.</p>
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>Caja</th>
            <th>Cajero</th>
            <th>Abierto desde</th>
            <th>
              <span className="solo-lector">Cerrar</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {turnos.map((turno) => (
            <tr key={turno.id}>
              <td>{turno.cajaNombre}</td>
              <td>{turno.usuarioNombre}</td>
              <td>{fecha(turno.abiertoEn)}</td>
              <td>
                <button
                  type="button"
                  aria-label={`Cerrar el turno de ${turno.usuarioNombre} en ${turno.cajaNombre}`}
                  onClick={() => onElegir(turno)}
                >
                  Cerrar este turno
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Crit. 1 y 2: lo cobrado en el turno y el mismo formulario del cierre propio; el cierre queda a nombre de ambos. */
function ResumenDeOtro({ turno, onCerrada }: { turno: TurnoAbierto; onCerrada: (hecho: CierreHecho) => void }) {
  const resumen = useQuery({
    queryKey: ['turnos', turno.id, 'cierre'],
    queryFn: () => obtenerResumenDeTurno(turno.id),
  })
  if (resumen.isPending) return <p className="tarjeta">Calculando lo cobrado en el turno…</p>
  if (resumen.error) return <p className="tarjeta error">{resumen.error.message}</p>
  return (
    <FormularioCierre
      resumen={resumen.data}
      cajaNombre={turno.cajaNombre}
      titulo={`Cerrar el turno de ${turno.usuarioNombre} en ${turno.cajaNombre}`}
      cerrar={(totalLote, justificacion) => cerrarTurnoDeOtro(turno.id, totalLote, justificacion)}
      onCerrada={onCerrada}
    />
  )
}
