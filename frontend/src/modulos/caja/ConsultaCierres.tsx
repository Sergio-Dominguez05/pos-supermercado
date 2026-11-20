import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  claveDetalleCierre,
  CLAVE_OPCIONES_CIERRES,
  consultarCierres,
  obtenerDetalleCierre,
  obtenerOpcionesCierres,
  reimprimirCierre,
  type DetalleCierre,
  type FiltrosCierres,
  type OpcionesCierres,
  type PaginaCierres,
} from '../../api/turnos'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { fechaYHora, filtrosIniciales, rotuloCierres } from './cierres'
import { ResumenCobrado } from './ResumenCobrado'

const SIN_PERMISO = 'No tiene permiso para consultar los cierres de caja.'

/** HU-09.13: los cierres anteriores de todas las cajas, para el Supervisor y el Administrador. */
export function PaginaConsultaCierres() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Cierres de caja</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Cierres de caja
      </p>
      {usuario.permisos.includes('CAJA_VER_CIERRES') ? <Consulta /> : <p className="tarjeta error">{SIN_PERMISO}</p>}
    </main>
  )
}

function Consulta() {
  const opciones = useQuery({ queryKey: CLAVE_OPCIONES_CIERRES, queryFn: obtenerOpcionesCierres })
  const [filtros, setFiltros] = useState<FiltrosCierres>(() => filtrosIniciales())
  const [pagina, setPagina] = useState(0)
  const cierres = useQuery({
    queryKey: ['cierres', 'consulta', filtros, pagina],
    queryFn: () => consultarCierres(filtros, pagina),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <FiltrosDeCierres
        iniciales={filtros}
        opciones={opciones.data}
        onBuscar={(elegidos) => {
          setFiltros(elegidos)
          setPagina(0)
        }}
      />
      {cierres.isPending && <p className="tarjeta">Buscando cierres…</p>}
      {cierres.error && (
        <p className="tarjeta error" role="alert">
          {cierres.error.message}
        </p>
      )}
      {cierres.data && <TablaCierres pagina={cierres.data} onPagina={setPagina} />}
    </>
  )
}

/** Crit. 2: por fecha (los últimos 7 días al abrir), caja, cajero y si tuvo diferencia. */
export function FiltrosDeCierres({
  iniciales,
  opciones,
  onBuscar,
}: {
  iniciales: FiltrosCierres
  opciones?: OpcionesCierres
  onBuscar: (filtros: FiltrosCierres) => void
}) {
  const [filtros, setFiltros] = useState(iniciales)
  const cambiar = (cambio: Partial<FiltrosCierres>) => setFiltros((actuales) => ({ ...actuales, ...cambio }))
  return (
    <form
      className="tarjeta"
      aria-label="Filtrar cierres"
      onSubmit={(evento) => {
        evento.preventDefault()
        onBuscar(filtros)
      }}
    >
      <div className="barra-filtros">
        <label>
          Desde
          <input
            name="desde"
            type="date"
            value={filtros.desde}
            onChange={(evento) => cambiar({ desde: evento.target.value })}
          />
        </label>
        <label>
          Hasta
          <input
            name="hasta"
            type="date"
            value={filtros.hasta}
            onChange={(evento) => cambiar({ hasta: evento.target.value })}
          />
        </label>
        <label>
          Caja
          <select name="cajaId" value={filtros.cajaId} onChange={(evento) => cambiar({ cajaId: evento.target.value })}>
            <option value="">Todas</option>
            {opciones?.cajas.map((caja) => (
              <option key={caja.id} value={caja.id}>
                {caja.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Cajero
          <select
            name="cajeroId"
            value={filtros.cajeroId}
            onChange={(evento) => cambiar({ cajeroId: evento.target.value })}
          >
            <option value="">Todos</option>
            {opciones?.cajeros.map((cajero) => (
              <option key={cajero.id} value={cajero.id}>
                {cajero.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          <input
            name="soloConDiferencias"
            type="checkbox"
            checked={filtros.soloConDiferencias}
            onChange={(evento) => cambiar({ soloConDiferencias: evento.target.checked })}
          />{' '}
          Solo con diferencias
        </label>
        <button type="submit" className="boton">
          Buscar
        </button>
      </div>
    </form>
  )
}

/** Crit. 1: fecha, caja, cajero, total registrado, cierre de lote y diferencia; cada fila lleva al detalle. */
export function TablaCierres({
  pagina: datos,
  onPagina,
}: {
  pagina: PaginaCierres
  onPagina: (pagina: number) => void
}) {
  const { cierres, pagina, tamanoPagina, total } = datos
  return (
    <section aria-label="Cierres">
      <p className="tarjeta" role="status">
        {rotuloCierres(pagina, tamanoPagina, total, cierres.length)}
      </p>
      {cierres.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Cierre</th>
                <th>Caja</th>
                <th>Cajero</th>
                <th>Registrado</th>
                <th>Cierre de lote</th>
                <th>Diferencia</th>
              </tr>
            </thead>
            <tbody>
              {cierres.map((cierre) => (
                <tr key={cierre.turnoId}>
                  <td>
                    <Link to={`/caja/cierres/${cierre.turnoId}`}>{fechaYHora(cierre.cerradoEn)}</Link>
                  </td>
                  <td>{cierre.cajaNombre}</td>
                  <td>{cierre.usuarioNombre}</td>
                  <td>{formatoQuetzales(cierre.totalRegistrado)}</td>
                  <td>{formatoQuetzales(cierre.totalLote)}</td>
                  <td className={cierre.diferencia === 0 ? undefined : 'error'}>
                    {formatoQuetzales(cierre.diferencia)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {total > tamanoPagina && (
        <div className="acciones">
          <button type="button" disabled={pagina === 0} onClick={() => onPagina(pagina - 1)}>
            Anteriores
          </button>
          <button type="button" disabled={(pagina + 1) * tamanoPagina >= total} onClick={() => onPagina(pagina + 1)}>
            Siguientes
          </button>
        </div>
      )}
    </section>
  )
}

/** HU-09.13 crit. 3: el detalle de un cierre, con lo cobrado en el turno y la reimpresión del comprobante. */
export function PaginaDetalleCierre() {
  const { data: usuario } = useSesion()
  const { turnoId } = useParams()
  const id = Number(turnoId)
  const detalle = useQuery({
    queryKey: claveDetalleCierre(id),
    queryFn: () => obtenerDetalleCierre(id),
    enabled: Boolean(usuario?.permisos.includes('CAJA_VER_CIERRES')) && Number.isInteger(id),
  })
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Detalle del cierre</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/caja/cierres">Cierres de caja</Link> · Detalle
      </p>
      {!usuario.permisos.includes('CAJA_VER_CIERRES') ? (
        <p className="tarjeta error">{SIN_PERMISO}</p>
      ) : detalle.isPending ? (
        <p className="tarjeta">Cargando el cierre…</p>
      ) : detalle.error ? (
        <p className="tarjeta error">{detalle.error.message}</p>
      ) : (
        <VistaDetalleCierre detalle={detalle.data} />
      )}
    </main>
  )
}

export function VistaDetalleCierre({ detalle }: { detalle: DetalleCierre }) {
  const { cierre, resumen } = detalle
  const reimpresion = useMutation({ mutationFn: () => reimprimirCierre(cierre.turnoId) })
  return (
    <section className="tarjeta" aria-label="Cierre de caja">
      <h2>
        {cierre.cajaNombre} · {cierre.usuarioNombre}
      </h2>
      <dl className="resumen">
        <dt>Apertura</dt>
        <dd>{fechaYHora(cierre.abiertoEn)}</dd>
        <dt>Cierre</dt>
        <dd>{fechaYHora(cierre.cerradoEn)}</dd>
        <dt>Cerrado por</dt>
        <dd>{cierre.cerradoPor}</dd>
        <dt>Total registrado</dt>
        <dd>{formatoQuetzales(cierre.totalRegistrado)}</dd>
        <dt>Cierre de lote</dt>
        <dd>{formatoQuetzales(cierre.totalLote)}</dd>
        <dt>
          <strong>Diferencia</strong>
        </dt>
        <dd className={cierre.diferencia === 0 ? 'exito' : 'error'}>
          <strong>{formatoQuetzales(cierre.diferencia)}</strong>
        </dd>
        {cierre.justificacion && (
          <>
            <dt>Justificación</dt>
            <dd>{cierre.justificacion}</dd>
          </>
        )}
      </dl>
      <h3>Lo cobrado en el turno</h3>
      <ResumenCobrado resumen={resumen} />
      <div className="acciones">
        <button type="button" disabled={reimpresion.isPending} onClick={() => reimpresion.mutate()}>
          {reimpresion.isPending ? 'Imprimiendo…' : 'Reimprimir el comprobante'}
        </button>
      </div>
      {reimpresion.data?.mensaje && (
        <p className={reimpresion.data.impresa ? 'exito' : 'pendiente'} role="status">
          {reimpresion.data.mensaje}
        </p>
      )}
      {reimpresion.error && (
        <p className="error" role="alert">
          {reimpresion.error.message}
        </p>
      )}
    </section>
  )
}
