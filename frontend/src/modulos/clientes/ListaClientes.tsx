import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  claveListaClientes,
  FILTRO_CLIENTES_INICIAL,
  listarClientes,
  type Cliente,
  type FiltroClientes,
} from '../../api/clientes'
import { useSesion } from '../login/sesion'
import { EdicionCliente } from './EdicionCliente'
import { clienteDeLista, fechaUltimaCompra, identificacionCliente, resumenClientes } from './textos'

/** HU-05.10: directorio paginado de clientes registrados; HU-05.09: desde aquí también se corrigen sus datos. */
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
  const [editado, setEditado] = useState<Cliente>()
  const consultas = useQueryClient()
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
                <th>
                  <span className="solo-lector">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {pagina.clientes.map((cliente) => (
                <tr key={cliente.id} className={cliente.id === editado?.id ? 'seleccionada' : undefined}>
                  <td>{identificacionCliente(cliente)}</td>
                  <td>{cliente.nombre}</td>
                  <td>{cliente.direccion}</td>
                  <td>{cliente.correo ?? '—'}</td>
                  <td>{fechaUltimaCompra(cliente.ultimaCompra)}</td>
                  <td>
                    <button
                      type="button"
                      className="boton-texto"
                      aria-label={`Editar a ${cliente.nombre}`}
                      onClick={() => setEditado(clienteDeLista(cliente))}
                    >
                      Editar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {/* HU-05.09: corregir nombre, dirección y correo; el NIT o CUI no se edita. */}
      {editado && (
        <EdicionCliente
          key={editado.id}
          cliente={editado}
          onCancelar={() => setEditado(undefined)}
          onGuardado={() => {
            setEditado(undefined)
            void consultas.invalidateQueries({ queryKey: ['clientes', 'lista'] })
          }}
        />
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
