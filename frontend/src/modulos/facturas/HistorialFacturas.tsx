import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  CLAVE_OPCIONES_HISTORIAL,
  FILTROS_HISTORIAL_VACIOS,
  obtenerHistorial,
  obtenerOpcionesHistorial,
  type FiltrosHistorial,
  type HistorialFacturas,
  type OpcionesHistorial,
} from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { nitConGuion } from '../clientes/textos'
import { useSesion } from '../login/sesion'
import { ESTADOS_DE_FACTURA, numeroDeFactura, rotuloPagina } from './textos'

const fecha = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Guatemala' })

/** HU-08.13: el historial de facturas, para el Supervisor y el Administrador (permiso de anular facturas). */
export function PaginaHistorialFacturas() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Historial de facturas</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Historial de facturas
      </p>
      {usuario.permisos.includes('FACTURA_ANULAR') ? (
        <ConsultaHistorial />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar el historial de facturas.</p>
      )}
    </main>
  )
}

function ConsultaHistorial() {
  const opciones = useQuery({ queryKey: CLAVE_OPCIONES_HISTORIAL, queryFn: obtenerOpcionesHistorial })
  const [filtros, setFiltros] = useState<FiltrosHistorial>(FILTROS_HISTORIAL_VACIOS)
  const [pagina, setPagina] = useState(0)
  const historial = useQuery({
    queryKey: ['facturas', 'historial', filtros, pagina],
    queryFn: () => obtenerHistorial(filtros, pagina),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <FiltrosDelHistorial
        opciones={opciones.data}
        onBuscar={(elegidos) => {
          setFiltros(elegidos)
          setPagina(0)
        }}
      />
      {historial.isPending && <p className="tarjeta">Buscando facturas…</p>}
      {historial.error && (
        <p className="tarjeta error" role="alert">
          {historial.error.message}
        </p>
      )}
      {historial.data && <TablaHistorial historial={historial.data} onPagina={setPagina} />}
    </>
  )
}

/** Crit. 2 y 3: filtros por fecha, estado, caja, cajero y cliente, y búsqueda por número o autorización. */
export function FiltrosDelHistorial({
  opciones,
  onBuscar,
}: {
  opciones?: OpcionesHistorial
  onBuscar: (filtros: FiltrosHistorial) => void
}) {
  const [filtros, setFiltros] = useState<FiltrosHistorial>(FILTROS_HISTORIAL_VACIOS)
  const cambiar = (cambio: Partial<FiltrosHistorial>) => setFiltros((actuales) => ({ ...actuales, ...cambio }))
  return (
    <form
      className="tarjeta"
      aria-label="Filtrar facturas"
      onSubmit={(evento) => {
        evento.preventDefault()
        onBuscar(filtros)
      }}
    >
      <div className="barra-filtros">
        <label>
          Número o autorización
          <input
            name="buscar"
            maxLength={60}
            placeholder="Serie y número, autorización, acceso o venta"
            value={filtros.buscar}
            onChange={(evento) => cambiar({ buscar: evento.target.value })}
          />
        </label>
        <label>
          Cliente
          <input
            name="cliente"
            maxLength={60}
            placeholder="NIT o nombre"
            value={filtros.cliente}
            onChange={(evento) => cambiar({ cliente: evento.target.value })}
          />
        </label>
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
          Estado
          <select name="estado" value={filtros.estado} onChange={(evento) => cambiar({ estado: evento.target.value })}>
            <option value="">Todos</option>
            {Object.entries(ESTADOS_DE_FACTURA).map(([valor, etiqueta]) => (
              <option key={valor} value={valor}>
                {etiqueta}
              </option>
            ))}
          </select>
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
        <button type="submit" className="boton">
          Buscar
        </button>
      </div>
    </form>
  )
}

/** Crit. 1: fecha, factura, autorización, cliente, total, estado y caja; cada fila lleva al detalle (HU-08.14). */
export function TablaHistorial({
  historial,
  onPagina,
}: {
  historial: HistorialFacturas
  onPagina: (pagina: number) => void
}) {
  const { facturas, pagina, tamanoPagina, total } = historial
  return (
    <section aria-label="Facturas">
      <p className="tarjeta" role="status">
        {rotuloPagina(pagina, tamanoPagina, total, facturas.length)}
      </p>
      {facturas.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Emitida</th>
                <th>Factura</th>
                <th>Autorización</th>
                <th>Cliente</th>
                <th>Total</th>
                <th>Estado</th>
                <th>Caja y cajero</th>
              </tr>
            </thead>
            <tbody>
              {facturas.map((factura) => (
                <tr key={factura.facturaId}>
                  <td>{fecha(factura.emitidaEn)}</td>
                  <td>
                    <Link to={`/facturas/${factura.facturaId}`}>{numeroDeFactura(factura)}</Link>
                    <small> · venta N.º {factura.ventaNumero}</small>
                  </td>
                  <td>
                    <small>{factura.autorizacion ? factura.autorizacion.toUpperCase() : '—'}</small>
                  </td>
                  <td>
                    {factura.receptorId === 'CF' ? 'CF' : nitConGuion(factura.receptorId)} · {factura.receptorNombre}
                  </td>
                  <td>{formatoQuetzales(factura.total)}</td>
                  <td>{ESTADOS_DE_FACTURA[factura.estado] ?? factura.estado}</td>
                  <td>
                    {factura.caja} <small>· {factura.cajero}</small>
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
