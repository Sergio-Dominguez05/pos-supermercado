import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  descargarReporte,
  type Agrupacion,
  type EncabezadoReporte,
  type Exportacion,
  type FiltrosVentas,
  type FormatoExportacion,
  type OpcionesDeFiltro,
} from '../../api/reportes'
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
  conDiferencias = false,
  conAnulador = false,
  onGenerar,
}: {
  opciones: OpcionesDeFiltro
  generando?: boolean
  conAgrupacion?: boolean
  conTarjeta?: boolean
  /** HU-10.08 crit. 3: la casilla "Solo con diferencias". */
  conDiferencias?: boolean
  /** HU-10.09 crit. 2: el filtro por quien anuló. */
  conAnulador?: boolean
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
        {conAnulador && (
          <label>
            Anulada por
            <select
              name="anuladaPorId"
              value={filtros.anuladaPorId}
              onChange={(evento) => cambiar({ anuladaPorId: evento.target.value })}
            >
              <option value="">Cualquiera</option>
              {opciones.anuladores.map((usuario) => (
                <option key={usuario.valor} value={usuario.valor}>
                  {usuario.etiqueta}
                </option>
              ))}
            </select>
          </label>
        )}
        {conDiferencias && (
          <label className="casilla">
            <input
              type="checkbox"
              name="soloConDiferencias"
              checked={filtros.soloConDiferencias}
              onChange={(evento) => cambiar({ soloConDiferencias: evento.target.checked })}
            />
            Solo con diferencias
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
export function EncabezadoDeReporte({
  encabezado,
  exportacion,
}: {
  encabezado: EncabezadoReporte
  /** HU-10.13 y HU-10.14: con qué filtros exportar lo que se ve; sin esto no se muestran los botones. */
  exportacion?: Exportacion
}) {
  return (
    <header className="tarjeta" aria-label="Encabezado del reporte">
      <div className="encabezado-reporte">
        <h2>{encabezado.titulo}</h2>
        {exportacion && <BotonesExportar exportacion={exportacion} />}
      </div>
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

/** HU-10.13 y HU-10.14: exportar el reporte a PDF o a Excel con los mismos filtros. */
export function BotonesExportar({ exportacion }: { exportacion: Exportacion }) {
  const descarga = useMutation({ mutationFn: (formato: FormatoExportacion) => descargarReporte(exportacion, formato) })
  return (
    <div className="exportar">
      <div className="acciones" role="group" aria-label="Exportar el reporte">
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('PDF')}>
          {descarga.isPending && descarga.variables === 'PDF' ? 'Exportando…' : 'Exportar a PDF'}
        </button>
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('XLSX')}>
          {descarga.isPending && descarga.variables === 'XLSX' ? 'Exportando…' : 'Exportar a Excel'}
        </button>
      </div>
      {descarga.error && (
        <p className="error" role="alert">
          {descarga.error.message}
        </p>
      )}
    </div>
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
