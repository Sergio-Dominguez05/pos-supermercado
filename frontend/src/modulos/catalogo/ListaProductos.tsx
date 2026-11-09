import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  claveListaProductos,
  FILTRO_PRODUCTOS_INICIAL,
  listarProductos,
  type FiltroProductos,
} from '../../api/productos'
import { useSesion } from '../login/sesion'
import { formatoFecha, formatoQuetzales, resumenPagina } from './textos'

/**
 * HU-03.04: lista del catálogo con búsqueda por código o descripción, filtro por estado, orden y paginación.
 * Todos los que consultan el catálogo la ven; crear y editar solo aparecen con el permiso (crit. 6).
 */
export function ListaProductos() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Catálogo</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Catálogo
      </p>
      {/* El servidor exige el permiso; esto solo evita mostrar una lista que no se podrá cargar. */}
      {usuario.permisos.includes('PRODUCTO_CONSULTAR') ? (
        <TablaProductos puedeEditar={usuario.permisos.includes('PRODUCTO_EDITAR')} />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar el catálogo.</p>
      )}
    </main>
  )
}

export function TablaProductos({ puedeEditar }: { puedeEditar: boolean }) {
  const [filtro, setFiltro] = useState<FiltroProductos>(FILTRO_PRODUCTOS_INICIAL)
  const { data: pagina, error, isPending } = useQuery({
    queryKey: claveListaProductos(filtro),
    queryFn: () => listarProductos(filtro),
    // Mientras llega la nueva página se sigue viendo la anterior, sin parpadeo.
    placeholderData: keepPreviousData,
  })
  // Cambiar la búsqueda, el filtro o el orden vuelve a la primera página.
  const cambiar = (cambios: Partial<FiltroProductos>) => setFiltro((actual) => ({ ...actual, pagina: 0, ...cambios }))
  const irA = (numero: number) => setFiltro((actual) => ({ ...actual, pagina: numero }))
  const totalPaginas = pagina ? Math.ceil(pagina.total / pagina.tamano) : 0

  return (
    <section className="tarjeta">
      <div className="barra-filtros">
        <label>
          Buscar
          <input
            type="search"
            name="q"
            placeholder="Código o descripción"
            maxLength={100}
            value={filtro.q}
            onChange={(evento) => cambiar({ q: evento.target.value })}
          />
        </label>
        <label>
          Estado
          <select
            name="activo"
            value={filtro.activo}
            onChange={(evento) => cambiar({ activo: evento.target.value as FiltroProductos['activo'] })}
          >
            <option value="">Todos</option>
            <option value="true">Activos</option>
            <option value="false">Inactivos</option>
          </select>
        </label>
        <label>
          Ordenar por
          <select
            name="orden"
            value={`${filtro.orden}:${filtro.descendente ? 'desc' : 'asc'}`}
            onChange={(evento) => {
              const [orden, direccion] = evento.target.value.split(':')
              cambiar({ orden: orden as FiltroProductos['orden'], descendente: direccion === 'desc' })
            }}
          >
            <option value="DESCRIPCION:asc">Descripción (A-Z)</option>
            <option value="DESCRIPCION:desc">Descripción (Z-A)</option>
            <option value="PRECIO:asc">Precio (menor a mayor)</option>
            <option value="PRECIO:desc">Precio (mayor a menor)</option>
            <option value="MODIFICADO:desc">Modificados recientemente</option>
            <option value="MODIFICADO:asc">Modificados hace más tiempo</option>
          </select>
        </label>
        <Link className="boton" to="/catalogo/buscar">
          Buscar producto
        </Link>
        {puedeEditar && (
          <Link className="boton" to="/catalogo/nuevo">
            Nuevo producto
          </Link>
        )}
      </div>

      {error && (
        <p className="error" role="alert">
          {error.message}
        </p>
      )}
      {isPending && <p>Cargando productos…</p>}
      {pagina && pagina.productos.length === 0 && <p className="pendiente">No hay productos que coincidan.</p>}
      {pagina && pagina.productos.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Código</th>
                <th>Descripción</th>
                <th>Precio</th>
                <th>Estado</th>
                <th>Última modificación</th>
                {puedeEditar && (
                  <th>
                    <span className="solo-lector">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {pagina.productos.map((producto) => (
                <tr key={producto.id}>
                  <td>{producto.codigoBarras}</td>
                  <td>{producto.descripcion}</td>
                  <td>{formatoQuetzales(producto.precio)}</td>
                  <td>
                    <span className={producto.activo ? 'estado estado-activo' : 'estado estado-inactivo'}>
                      {producto.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>{formatoFecha(producto.modificadoEn)}</td>
                  {puedeEditar && (
                    <td className="acciones">
                      <Link to={`/catalogo/${producto.id}/editar`} aria-label={`Editar ${producto.descripcion}`}>
                        Editar
                      </Link>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {pagina && (
        <nav className="barra-filtros" aria-label="Páginas del catálogo">
          <button type="button" className="boton-texto" disabled={filtro.pagina === 0} onClick={() => irA(filtro.pagina - 1)}>
            Anterior
          </button>
          <span>{resumenPagina(filtro.pagina, pagina.tamano, pagina.total)}</span>
          <button
            type="button"
            className="boton-texto"
            disabled={filtro.pagina + 1 >= totalPaginas}
            onClick={() => irA(filtro.pagina + 1)}
          >
            Siguiente
          </button>
        </nav>
      )}
    </section>
  )
}
