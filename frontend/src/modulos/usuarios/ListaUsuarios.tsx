import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { claveUsuarios, FILTRO_INICIAL, listarUsuarios, type FiltroUsuarios } from '../../api/usuarios'
import { useSesion } from '../login/sesion'
import { formatearUltimoAcceso } from './formato'

/** HU-01.06: lista de usuarios con búsqueda por nombre o usuario y filtros por rol y estado. */
export function ListaUsuarios() {
  const { data: usuario } = useSesion()
  // Al volver de crear (HU-01.05) o editar (HU-01.07) un usuario llega el aviso a mostrar.
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Usuarios</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Usuarios
      </p>
      {aviso && (
        <p className="tarjeta exito" role="status">
          {aviso}
        </p>
      )}
      {/* El servidor exige el permiso; esto solo evita mostrar una lista que no se podrá cargar. */}
      {usuario.permisos.includes('USUARIO_ADMINISTRAR') ? (
        <TablaUsuarios />
      ) : (
        <p className="tarjeta error">No tiene permiso para administrar usuarios.</p>
      )}
    </main>
  )
}

function TablaUsuarios() {
  const [filtro, setFiltro] = useState<FiltroUsuarios>(FILTRO_INICIAL)
  const { data: usuarios, error, isPending } = useQuery({
    queryKey: claveUsuarios(filtro),
    queryFn: () => listarUsuarios(filtro),
    // Mientras llega la nueva búsqueda se sigue viendo la anterior, sin parpadeo.
    placeholderData: keepPreviousData,
  })
  const cambiar = (cambios: Partial<FiltroUsuarios>) => setFiltro((actual) => ({ ...actual, ...cambios }))

  return (
    <section className="tarjeta">
      <div className="barra-filtros">
        <label>
          Buscar
          <input
            type="search"
            name="q"
            placeholder="Nombre o usuario"
            maxLength={100}
            value={filtro.q}
            onChange={(evento) => cambiar({ q: evento.target.value })}
          />
        </label>
        <label>
          Rol
          <select
            name="rol"
            value={filtro.rol}
            onChange={(evento) => cambiar({ rol: evento.target.value as FiltroUsuarios['rol'] })}
          >
            <option value="">Todos</option>
            <option value="ADMIN">Administrador</option>
            <option value="SUPERVISOR">Supervisor</option>
            <option value="CAJERO">Cajero</option>
          </select>
        </label>
        <label>
          Estado
          <select
            name="activo"
            value={filtro.activo}
            onChange={(evento) => cambiar({ activo: evento.target.value as FiltroUsuarios['activo'] })}
          >
            <option value="">Todos</option>
            <option value="true">Activos</option>
            <option value="false">Inactivos</option>
          </select>
        </label>
        <Link className="boton" to="/usuarios/nuevo">
          Nuevo usuario
        </Link>
      </div>

      {error && (
        <p className="error" role="alert">
          {error.message}
        </p>
      )}
      {isPending && <p>Cargando usuarios…</p>}
      {usuarios && usuarios.length === 0 && <p className="pendiente">No hay usuarios que coincidan.</p>}
      {usuarios && usuarios.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre completo</th>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Último acceso</th>
                <th>
                  <span className="solo-lector">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((fila) => (
                <tr key={fila.id}>
                  <td>{fila.nombreCompleto}</td>
                  <td>{fila.username}</td>
                  <td>{fila.rolNombre}</td>
                  <td>
                    <span className={fila.activo ? 'estado estado-activo' : 'estado estado-inactivo'}>
                      {fila.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>{formatearUltimoAcceso(fila.ultimoAcceso)}</td>
                  <td>
                    <Link to={`/usuarios/${fila.id}/editar`} aria-label={`Editar ${fila.username}`}>
                      Editar
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
