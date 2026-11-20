import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  cerrarCaja,
  CLAVE_CIERRES,
  CLAVE_RESUMEN_CIERRE,
  CLAVE_TURNO_ACTUAL,
  listarCierres,
  obtenerResumenCierre,
  reimprimirCierre,
  type CierreDeTurno,
  type CierreHecho,
  type ResumenDeTurno,
} from '../../api/turnos'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { confirmacionCierre, diferenciaDeCierre, totalLoteValido } from './textos'
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
  return <FormularioCierre resumen={data} cajaNombre={cajaNombre} onCerrada={onCerrada} />
}

/**
 * - 09.08 crit. 2 y 3: el total del cierre de lote de la terminal y la diferencia con lo registrado.
 * - 09.09 crit. 1: con diferencia, la justificación es obligatoria.
 * - 09.08 crit. 1: si hay ventas por resolver, el servidor lo rechaza y aquí se muestra el motivo.
 */
export function FormularioCierre({
  resumen,
  cajaNombre,
  onCerrada,
}: {
  resumen: ResumenDeTurno
  cajaNombre: string
  onCerrada: (hecho: CierreHecho) => void
}) {
  const clienteConsultas = useQueryClient()
  const [lote, setLote] = useState('')
  const [justificacion, setJustificacion] = useState('')
  const valido = totalLoteValido(lote)
  const diferencia = valido ? diferenciaDeCierre(Number(lote), resumen.totalRegistrado) : 0
  const cierre = useMutation({
    mutationFn: () => cerrarCaja(Number(lote), justificacion),
    onSuccess: async (hecho) => {
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_TURNO_ACTUAL })
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_CIERRES })
      onCerrada(hecho)
    },
  })

  return (
    <section className="tarjeta formulario" aria-label="Cerrar la caja">
      <h2>Cerrar la caja</h2>
      <ResumenCobrado resumen={resumen} />
      <form
        className="formulario"
        onSubmit={(evento) => {
          evento.preventDefault()
          if (!valido || !window.confirm(confirmacionCierre(cajaNombre, diferencia))) return
          cierre.mutate()
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
            value={lote}
            onChange={(evento) => setLote(evento.target.value)}
          />
        </label>
        {valido && (
          <p className={diferencia === 0 ? 'exito' : 'error'} role="status">
            Diferencia: {formatoQuetzales(diferencia)}
            {diferencia === 0 ? ' (coincide con lo registrado)' : ''}
          </p>
        )}
        {valido && diferencia !== 0 && (
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
        <button type="submit" disabled={cierre.isPending || !valido}>
          {cierre.isPending ? 'Cerrando…' : 'Cerrar la caja'}
        </button>
      </form>
    </section>
  )
}

export function ResumenCobrado({ resumen }: { resumen: ResumenDeTurno }) {
  return (
    <dl className="resumen">
      <dt>Ventas</dt>
      <dd>{resumen.ventas}</dd>
      <dt>Tarjeta de débito</dt>
      <dd>{formatoQuetzales(resumen.debito)}</dd>
      <dt>Tarjeta de crédito</dt>
      <dd>{formatoQuetzales(resumen.credito)}</dd>
      <dt>Facturas anuladas ({resumen.anuladas})</dt>
      <dd>{formatoQuetzales(resumen.totalAnulado)}</dd>
      <dt>
        <strong>Total registrado</strong>
      </dt>
      <dd>
        <strong>{formatoQuetzales(resumen.totalRegistrado)}</strong>
      </dd>
      {resumen.contingencias > 0 && (
        <>
          <dt>En contingencia</dt>
          <dd>{resumen.contingencias} por certificar</dd>
        </>
      )}
    </dl>
  )
}

function CierreTerminado({ hecho }: { hecho: CierreHecho }) {
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
