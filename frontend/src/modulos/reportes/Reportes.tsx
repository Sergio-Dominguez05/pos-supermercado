import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  CLAVE_FILTROS_REPORTE,
  generarReporteVentas,
  obtenerOpcionesDeFiltro,
  type Agrupacion,
  type EncabezadoReporte,
  type FilaVentasPeriodo,
  type FiltrosVentas,
  type OpcionesDeFiltro,
  type ReporteVentasPeriodo,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { filtrosIniciales, PERMISO_REPORTES } from './filtros'

const fechaHora = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Guatemala',
  })

/** HU-10.01 y HU-10.02: el reporte de ventas por período, con los filtros comunes. */
export function PaginaReportes() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Reportes</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Reportes · Ventas por período
      </p>
      {usuario.permisos.includes(PERMISO_REPORTES) ? (
        <CargaReportes />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar reportes.</p>
      )}
    </main>
  )
}

function CargaReportes() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_FILTROS_REPORTE,
    queryFn: obtenerOpcionesDeFiltro,
  })
  if (isPending) return <p className="tarjeta">Cargando filtros…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <ReporteDeVentas opciones={data} />
}

export function ReporteDeVentas({ opciones }: { opciones: OpcionesDeFiltro }) {
  // Una mutación y no una consulta: cada generación queda en la bitácora (HU-10.15 crit. 3), así que no se repite sola.
  const generacion = useMutation({ mutationFn: generarReporteVentas })
  return (
    <>
      <FormularioFiltros opciones={opciones} generando={generacion.isPending} onGenerar={generacion.mutate} />
      {generacion.error && (
        <p className="tarjeta error" role="alert">
          {generacion.error.message}
        </p>
      )}
      {generacion.data && <TablaVentasPeriodo reporte={generacion.data} />}
    </>
  )
}

export function FormularioFiltros({
  opciones,
  generando = false,
  onGenerar,
}: {
  opciones: OpcionesDeFiltro
  generando?: boolean
  onGenerar: (filtros: FiltrosVentas) => void
}) {
  const [filtros, setFiltros] = useState(() => filtrosIniciales(opciones))
  const cambiar = (cambio: Partial<FiltrosVentas>) => setFiltros((actuales) => ({ ...actuales, ...cambio }))

  return (
    <form
      className="tarjeta"
      aria-label="Filtros del reporte"
      onSubmit={(evento) => {
        evento.preventDefault()
        onGenerar(filtros)
      }}
    >
      {/* HU-10.01 crit. 3: los rangos rápidos llenan las fechas. */}
      <div className="rangos" role="group" aria-label="Rangos rápidos">
        {opciones.rangos.map((rango) => (
          <button
            key={rango.codigo}
            type="button"
            aria-pressed={filtros.desde === rango.desde && filtros.hasta === rango.hasta}
            onClick={() => cambiar({ desde: rango.desde, hasta: rango.hasta })}
          >
            {rango.etiqueta}
          </button>
        ))}
      </div>
      <div className="barra-filtros">
        <label>
          Desde
          <input
            name="desde"
            type="date"
            required
            value={filtros.desde}
            onChange={(evento) => cambiar({ desde: evento.target.value })}
          />
        </label>
        <label>
          Hasta
          <input
            name="hasta"
            type="date"
            required
            value={filtros.hasta}
            onChange={(evento) => cambiar({ hasta: evento.target.value })}
          />
        </label>
        <label>
          Caja
          <select name="cajaId" value={filtros.cajaId} onChange={(evento) => cambiar({ cajaId: evento.target.value })}>
            <option value="">Todas</option>
            {opciones.cajas.map((caja) => (
              <option key={caja.valor} value={caja.valor}>
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
            {opciones.cajeros.map((cajero) => (
              <option key={cajero.valor} value={cajero.valor}>
                {cajero.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Agrupar por
          <select
            name="agrupacion"
            value={filtros.agrupacion}
            onChange={(evento) => cambiar({ agrupacion: evento.target.value as Agrupacion })}
          >
            {opciones.agrupaciones.map((agrupacion) => (
              <option key={agrupacion.valor} value={agrupacion.valor}>
                {agrupacion.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="boton" disabled={generando}>
          {generando ? 'Generando…' : 'Generar reporte'}
        </button>
      </div>
    </form>
  )
}

/** HU-10.01 crit. 4: el título, los filtros aplicados, quién lo generó y cuándo. */
export function EncabezadoDeReporte({ encabezado }: { encabezado: EncabezadoReporte }) {
  return (
    <header className="tarjeta" aria-label="Encabezado del reporte">
      <h2>{encabezado.titulo}</h2>
      <ul>
        {encabezado.filtros.map((filtro) => (
          <li key={filtro.etiqueta}>
            <strong>{filtro.etiqueta}:</strong> {filtro.valor}
          </li>
        ))}
      </ul>
      <p>
        <small>
          Generado el {fechaHora(encabezado.generadoEn)} por {encabezado.generadoPor}
        </small>
      </p>
    </header>
  )
}

const celdas = (fila: FilaVentasPeriodo) => (
  <>
    <td>{fila.cantidadVentas}</td>
    <td>{formatoQuetzales(fila.subtotal)}</td>
    <td>{formatoQuetzales(fila.iva)}</td>
    <td>{formatoQuetzales(fila.total)}</td>
    <td>{formatoQuetzales(fila.ticketPromedio)}</td>
  </>
)

/** HU-10.02 crit. 1 a 3: por período, sin las facturas anuladas, con la fila de totales. */
export function TablaVentasPeriodo({ reporte }: { reporte: ReporteVentasPeriodo }) {
  return (
    <section aria-label="Ventas por período">
      <EncabezadoDeReporte encabezado={reporte.encabezado} />
      {reporte.totales.cantidadVentas === 0 && <p className="tarjeta">No hay ventas con estos filtros.</p>}
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Período</th>
              <th>Ventas</th>
              <th>Subtotal</th>
              <th>IVA</th>
              <th>Total vendido</th>
              <th>Ticket promedio</th>
            </tr>
          </thead>
          <tbody>
            {reporte.filas.map((fila) => (
              <tr key={fila.desde}>
                <td>{fila.periodo}</td>
                {celdas(fila)}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">Total</th>
              {celdas(reporte.totales)}
            </tr>
          </tfoot>
        </table>
      </div>
      <p>
        <small>Las facturas anuladas no se suman a las ventas.</small>
      </p>
    </section>
  )
}
