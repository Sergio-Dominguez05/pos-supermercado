import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import {
  claveResumenTurno,
  CLAVE_TURNOS_ABIERTOS,
  listarTurnosAbiertos,
  obtenerResumenTurno,
  type Turno,
} from '../../api/turnos'
import { ResumenCobrado } from './ResumenCobrado'

/** HU-09.06: selección de un turno abierto y consulta de lo vendido sin cerrarlo. */
export function ResumenTurnosAbiertos() {
  const { data: turnos, error, isPending } = useQuery({
    queryKey: CLAVE_TURNOS_ABIERTOS,
    queryFn: listarTurnosAbiertos,
  })
  const [seleccionado, setSeleccionado] = useState<number>()
  const seleccionVisible = turnos?.some((turno) => turno.id === seleccionado) ? seleccionado : undefined

  if (isPending) return <p className="tarjeta">Consultando turnos abiertos…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  if (!turnos?.length) return <p className="tarjeta">No hay turnos abiertos para consultar.</p>

  return (
    <section className="tarjeta" aria-label="Resumen de turnos abiertos">
      <h2>Resumen del turno</h2>
      <p>Consulte lo vendido hasta este momento sin cerrar la caja.</p>
      <ListaTurnosAbiertos turnos={turnos} seleccionado={seleccionVisible} onSeleccionar={setSeleccionado} />
      {seleccionVisible !== undefined && <CargaResumen turnoId={seleccionVisible} />}
    </section>
  )
}

export function ListaTurnosAbiertos({
  turnos,
  seleccionado,
  onSeleccionar,
}: {
  turnos: Turno[]
  seleccionado?: number
  onSeleccionar: (turnoId: number) => void
}) {
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>Caja</th>
            <th>Cajero</th>
            <th>Apertura</th>
            <th><span className="solo-lector">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          {turnos.map((turno) => (
            <tr key={turno.id}>
              <td>{turno.cajaNombre}</td>
              <td>{turno.usuarioNombre}{turno.propio ? ' · Propio' : ''}</td>
              <td>{new Date(turno.abiertoEn).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })}</td>
              <td className="acciones">
                <button type="button" disabled={seleccionado === turno.id} onClick={() => onSeleccionar(turno.id)}>
                  {seleccionado === turno.id ? 'Resumen visible' : 'Ver resumen'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function CargaResumen({ turnoId }: { turnoId: number }) {
  const { data, error, isFetching, isPending, refetch } = useQuery({
    queryKey: claveResumenTurno(turnoId),
    queryFn: () => obtenerResumenTurno(turnoId),
    refetchOnMount: false,
  })
  if (isPending) return <p>Calculando el resumen…</p>
  if (error) return <p className="error" role="alert">{error.message}</p>
  return (
    <div>
      <ResumenCobrado resumen={data} />
      <button type="button" className="boton-texto" disabled={isFetching} onClick={() => refetch()}>
        {isFetching ? 'Actualizando…' : 'Actualizar resumen'}
      </button>
    </div>
  )
}
