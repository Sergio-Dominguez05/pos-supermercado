import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  claveListaClientes,
  FILTRO_CLIENTES_INICIAL,
  listarClientes,
  type FiltroClientes,
} from '../../api/clientes'
import { useSesion } from '../login/sesion'
import { fechaUltimaCompra, identificacionCliente, resumenClientes } from './textos'

/** HU-05.10: directorio paginado de clientes registrados. */
export function ListaClientes() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Clientes</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Clientes
      </p>
      {usuario.permisos.includes('CLIENTE_GESTIONAR') ? (
        <TablaClientes />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar los clientes.</p>
      )}
    </main>
  )
}

export function TablaClientes() {
  const [filtro, setFiltro] = useState<FiltroClientes>(FILTRO_CLIENTES_INICIAL)
  const { data: pagina, error, isPending } = useQuery({
    queryKey: claveListaClientes(filtro),
    queryFn: () => listarClientes(filtro),
    placeholderData: keepPreviousData,
  })
  const totalPaginas = pagina ? Math.ceil(pagina.total / pagina.tamano) : 0

  return (
    <section className="tarjeta">
      <div className="barra-filtros">
        <label>
          Buscar
          <input
            type="search"
            name="q"
            placeholder="NIT, CUI o nombre"
            maxLength={200}
            value={filtro.q}
            onChange={(evento) => setFiltro({ q: evento.target.value, pagina: 0 })}
          />
        </label>
      </div>

      {error && <p className="error" role="alert">{error.message}</p>}
      {isPending && <p>Cargando clientes…</p>}
      {pagina && pagina.clientes.length === 0 && <p className="pendiente">No hay clientes que coincidan.</p>}
      {pagina && pagina.clientes.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>NIT o CUI</th>
                <th>Nombre</th>
                <th>Dirección</th>
                <th>Correo</th>
                <th>Última compra</th>
              </tr>
            </thead>
            <tbody>
              {pagina.clientes.map((cliente) => (
                <tr key={cliente.id}>
                  <td>{identificacionCliente(cliente)}</td>
                  <td>{cliente.nombre}</td>
                  <td>{cliente.direccion}</td>
                  <td>{cliente.correo ?? '—'}</td>
                  <td>{fechaUltimaCompra(cliente.ultimaCompra)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {pagina && (
        <nav className="barra-filtros" aria-label="Páginas de clientes">
          <button
            type="button"
            className="boton-texto"
            disabled={filtro.pagina === 0}
            onClick={() => setFiltro((actual) => ({ ...actual, pagina: actual.pagina - 1 }))}
          >
            Anterior
          </button>
          <span>{resumenClientes(filtro.pagina, pagina.tamano, pagina.total)}</span>
          <button
            type="button"
            className="boton-texto"
            disabled={filtro.pagina + 1 >= totalPaginas}
            onClick={() => setFiltro((actual) => ({ ...actual, pagina: actual.pagina + 1 }))}
          >
            Siguiente
          </button>
        </nav>
      )}
    </section>
  )
}
