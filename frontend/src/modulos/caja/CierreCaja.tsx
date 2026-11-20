import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { ErrorDeApi } from '../../api/cliente'
import {
  cerrarCaja,
  claveResumenTurno,
  CLAVE_CIERRES,
  CLAVE_RESUMEN_CIERRE,
  CLAVE_TURNO_ACTUAL,
  CLAVE_TURNOS_ABIERTOS,
  compararCierre,
  listarCierres,
  obtenerResumenCierre,
  reimprimirCierre,
  type CierreDeTurno,
  type CierreHecho,
  type ResumenDeTurno,
} from '../../api/turnos'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { ResumenCobrado } from './ResumenCobrado'
import { confirmacionCierre, diferenciaDeCierre, totalLoteValido } from './textos'
import { ResumenTurnosAbiertos } from './ResumenTurnos'
import { useTurnoActual } from './turno'

const fecha = (iso: string) => new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })

/** HU-09.08 a HU-09.10: cerrar la caja propia e imprimir o reimprimir su comprobante. */
export function PaginaCierreCaja() {
  const { data: usuario } = useSesion()
  const { data: turno } = useTurnoActual(usuario)
  const [hecho, setHecho] = useState<CierreHecho>()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Caja</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Caja {usuario.cajaNombre ? `· ${usuario.cajaNombre}` : ''}
      </p>
      <ResumenTurnosAbiertos />
      {hecho ? (
        <CierreTerminado hecho={hecho} />
      ) : turno?.propio ? (
        <CargaResumen cajaNombre={turno.cajaNombre} onCerrada={setHecho} />
      ) : (
        <p className="tarjeta">No tiene un turno abierto en esta caja: no hay nada que cerrar.</p>
      )}
      <CierresAnteriores />
    </main>
  )
}

function CargaResumen({ cajaNombre, onCerrada }: { cajaNombre: string; onCerrada: (hecho: CierreHecho) => void }) {
  const { data, error, isPending } = useQuery({ queryKey: CLAVE_RESUMEN_CIERRE, queryFn: obtenerResumenCierre })
  if (isPending) return <p className="tarjeta">Calculando lo cobrado en el turno…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return (
    <FormularioCierre
      resumen={data.resumen}
      cierreCiego={data.cierreCiego}
      cajaNombre={cajaNombre}
      onCerrada={onCerrada}
    />
  )
}

/**
 * - 09.08 crit. 2 y 3: el total del cierre de lote de la terminal y la diferencia con lo registrado.
 * - 09.09 crit. 1: con diferencia, la justificación es obligatoria.
 * - 09.08 crit. 1: si hay ventas por resolver, el servidor lo rechaza y aquí se muestra el motivo.
 */
export function FormularioCierre({
  resumen,
  cierreCiego = false,
  cajaNombre,
  onCerrada,
  titulo = 'Cerrar la caja',
  cerrar = cerrarCaja,
}: {
  resumen: ResumenDeTurno | null
  cierreCiego?: boolean
  cajaNombre: string
  onCerrada: (hecho: CierreHecho) => void
  /** HU-09.12: el supervisor usa el mismo formulario para cerrar el turno de otro usuario. */
  titulo?: string
  cerrar?: (totalLote: number, justificacion: string) => Promise<CierreHecho>
}) {
  const clienteConsultas = useQueryClient()
  const [lote, setLote] = useState('')
  const [justificacion, setJustificacion] = useState('')
  const [comparacion, setComparacion] = useState<{
    totalLote: number
    resumen: ResumenDeTurno
    diferencia: number
  }>()
  const valido = totalLoteValido(lote)
  const comparacionActual = comparacion?.totalLote === Number(lote) ? comparacion : undefined
  const resumenVisible = comparacionActual?.resumen ?? resumen
  const diferencia = comparacionActual?.diferencia
    ?? (valido && resumenVisible ? diferenciaDeCierre(Number(lote), resumenVisible.totalRegistrado) : 0)
  const comparar = useMutation({
    mutationFn: compararCierre,
    onSuccess: (resultado, totalLote) => setComparacion({ totalLote, ...resultado }),
  })
  const cierre = useMutation({
    mutationFn: ({ totalLote, motivo }: { totalLote: number; motivo: string }) => cerrar(totalLote, motivo),
    onSuccess: async (hecho) => {
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_TURNO_ACTUAL })
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_CIERRES })
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_TURNOS_ABIERTOS })
      clienteConsultas.removeQueries({ queryKey: claveResumenTurno(hecho.comprobante.cierre.turnoId) })
      onCerrada(hecho)
    },
    onError: (error, solicitud) => {
      if (cierreCiego && error instanceof ErrorDeApi && error.codigo === 'JUSTIFICACION_REQUERIDA') {
        comparar.mutate(solicitud.totalLote)
      }
    },
  })

  return (
    <section className="tarjeta formulario" aria-label={titulo}>
      <h2>{titulo}</h2>
      {resumenVisible ? <ResumenCobrado resumen={resumenVisible} /> : (
        <p>Ingrese y confirme el total del cierre de lote para compararlo con lo registrado en el POS.</p>
      )}
      <form
        className="formulario"
        onSubmit={(evento) => {
          evento.preventDefault()
          if (!valido) return
          if (cierreCiego && !comparacionActual) {
            comparar.mutate(Number(lote))
            return
          }
          if (!window.confirm(confirmacionCierre(cajaNombre, diferencia))) return
          cierre.mutate({ totalLote: Number(lote), motivo: justificacion })
        }}
      >
        <label>
          Total del cierre de lote de la terminal
          <input
            name="totalLote"
            required
            inputMode="decimal"
            pattern="[0-9]+(\.[0-9]{1,2})?"
            placeholder="0.00"
            disabled={cierre.isPending || comparar.isPending}
            value={lote}
            onChange={(evento) => {
              setLote(evento.target.value)
              setComparacion(undefined)
              setJustificacion('')
            }}
          />
        </label>
        {valido && resumenVisible && (
          <p className={diferencia === 0 ? 'exito' : 'error'} role="status">
            Diferencia: {formatoQuetzales(diferencia)}
            {diferencia === 0 ? ' (coincide con lo registrado)' : ''}
          </p>
        )}
        {valido && resumenVisible && diferencia !== 0 && (
          <label>
            Justificación de la diferencia
            <textarea
              name="justificacion"
              required
              minLength={5}
              maxLength={500}
              value={justificacion}
              onChange={(evento) => setJustificacion(evento.target.value)}
            />
          </label>
        )}
        {cierre.error && (
          <p className="error" role="alert">
            {cierre.error.message}
          </p>
        )}
        {comparar.error && <p className="error" role="alert">{comparar.error.message}</p>}
        <button type="submit" disabled={cierre.isPending || comparar.isPending || !valido}>
          {comparar.isPending ? 'Comparando…'
            : cierre.isPending ? 'Cerrando…'
              : cierreCiego && !comparacionActual ? 'Confirmar monto' : 'Cerrar la caja'}
        </button>
      </form>
    </section>
  )
}

export function CierreTerminado({ hecho }: { hecho: CierreHecho }) {
  const { cierre } = hecho.comprobante
  return (
    <section className="tarjeta" aria-label="Cierre terminado">
      <p className="exito" role="status">
        Caja {cierre.cajaNombre} cerrada: registrado {formatoQuetzales(cierre.totalRegistrado)}, cierre de lote{' '}
        {formatoQuetzales(cierre.totalLote)}, diferencia {formatoQuetzales(cierre.diferencia)}.
      </p>
      {hecho.impresion.mensaje && <p className="pendiente">{hecho.impresion.mensaje}</p>}
      <p>Entregue el comprobante del cierre y el del cierre de lote al supervisor.</p>
    </section>
  )
}

/** HU-09.10 crit. 3: los cierres anteriores, para reimprimir su comprobante. */
function CierresAnteriores() {
  const { data } = useQuery({ queryKey: CLAVE_CIERRES, queryFn: listarCierres })
  if (!data || data.length === 0) return null
  return <ListaCierres cierres={data} />
}

export function ListaCierres({ cierres }: { cierres: CierreDeTurno[] }) {
  return (
    <section className="tarjeta" aria-label="Cierres anteriores">
      <h2>Cierres anteriores</h2>
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Caja y cajero</th>
              <th>Cierre</th>
              <th>Registrado</th>
              <th>Cierre de lote</th>
              <th>Diferencia</th>
              <th>
                <span className="solo-lector">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {cierres.map((cierre) => (
              <FilaCierre key={cierre.turnoId} cierre={cierre} />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function FilaCierre({ cierre }: { cierre: CierreDeTurno }) {
  const reimpresion = useMutation({ mutationFn: () => reimprimirCierre(cierre.turnoId) })
  return (
    <tr>
      <td>
        {cierre.cajaNombre} · {cierre.usuarioNombre}
      </td>
      <td>{fecha(cierre.cerradoEn)}</td>
      <td>{formatoQuetzales(cierre.totalRegistrado)}</td>
      <td>{formatoQuetzales(cierre.totalLote)}</td>
      <td>
        {formatoQuetzales(cierre.diferencia)}
        {cierre.justificacion && <small> · {cierre.justificacion}</small>}
      </td>
      <td className="acciones">
        <button
          type="button"
          disabled={reimpresion.isPending}
          onClick={() => reimpresion.mutate()}
          aria-label={`Reimprimir el cierre de ${cierre.cajaNombre} del ${fecha(cierre.cerradoEn)}`}
        >
          {reimpresion.isPending ? 'Imprimiendo…' : 'Reimprimir'}
        </button>
        {reimpresion.data?.mensaje && <p role="status">{reimpresion.data.mensaje}</p>}
        {reimpresion.error && (
          <p className="error" role="alert">
            {reimpresion.error.message}
          </p>
        )}
      </td>
    </tr>
  )
}
