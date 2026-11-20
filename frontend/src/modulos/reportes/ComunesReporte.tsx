import { useState } from 'react'
import type { Agrupacion, EncabezadoReporte, FiltrosVentas, OpcionesDeFiltro } from '../../api/reportes'
import { fechaHora, filtrosIniciales } from './filtros'

/**
 * HU-10.01: los filtros comunes de los reportes, con los rangos rápidos (crit. 3). La agrupación y el tipo de tarjeta
 * solo se muestran en los reportes que los usan (crit. 2: "cuando aplica").
 */
export function FormularioFiltros({
  opciones,
  generando = false,
  conAgrupacion = false,
  conTarjeta = false,
  onGenerar,
}: {
  opciones: OpcionesDeFiltro
  generando?: boolean
  conAgrupacion?: boolean
  conTarjeta?: boolean
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
        {conTarjeta && (
          <label>
            Tipo de tarjeta
            <select
              name="tipoTarjeta"
              value={filtros.tipoTarjeta}
              onChange={(evento) => cambiar({ tipoTarjeta: evento.target.value })}
            >
              <option value="">Todas</option>
              {opciones.tiposTarjeta.map((tipo) => (
                <option key={tipo.valor} value={tipo.valor}>
                  {tipo.etiqueta}
                </option>
              ))}
            </select>
          </label>
        )}
        {conAgrupacion && (
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
        )}
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

/** El error de una generación, por ejemplo con las fechas invertidas. */
export function ErrorDeReporte({ error }: { error: Error | null }) {
  if (!error) return null
  return (
    <p className="tarjeta error" role="alert">
      {error.message}
    </p>
  )
}
