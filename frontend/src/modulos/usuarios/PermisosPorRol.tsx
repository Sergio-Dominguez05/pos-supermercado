import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import {
  CLAVE_PERMISOS,
  guardarPermisosDeRol,
  obtenerMatrizPermisos,
  type MatrizPermisos,
  type RolConfigurable,
} from '../../api/permisos'
import { useSesion } from '../login/sesion'
import { asignacionDe, AVISO_SIGUIENTE_INICIO, rolesCambiados } from './permisos'

/** HU-01.10: el Administrador configura los permisos del Supervisor y del Cajero. */
export function PaginaPermisos() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Permisos por rol</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/usuarios">Usuarios</Link> · Permisos por rol
      </p>
      {usuario.permisos.includes('PERMISO_CONFIGURAR') ? (
        <CargaMatriz />
      ) : (
        <p className="tarjeta error">No tiene permiso para configurar los permisos por rol.</p>
      )}
    </main>
  )
}

function CargaMatriz() {
  const { data, error, isPending } = useQuery({ queryKey: CLAVE_PERMISOS, queryFn: obtenerMatrizPermisos })
  if (isPending) return <p className="tarjeta">Cargando permisos…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <EditorPermisos matriz={data} />
}

/**
 * Crit. 1: agrupados por módulo. Crit. 2: casillas del Supervisor y del Cajero. Crit. 3: el Administrador los tiene
 * todos, sin casilla para quitarlos. Crit. 4: los exclusivos del Administrador no se pueden marcar.
 */
export function EditorPermisos({ matriz }: { matriz: MatrizPermisos }) {
  const clienteConsultas = useQueryClient()
  const [guardada, setGuardada] = useState(() => asignacionDe(matriz))
  const [editada, setEditada] = useState(() => asignacionDe(matriz))
  const cambiados = rolesCambiados(guardada, editada)
  const guardado = useMutation({
    mutationFn: async () => {
      let ultima = matriz
      for (const rol of cambiados) ultima = await guardarPermisosDeRol(rol, [...editada[rol]].sort())
      return ultima
    },
    onSuccess: (nueva) => {
      clienteConsultas.setQueryData(CLAVE_PERMISOS, nueva)
      setGuardada(asignacionDe(nueva))
      setEditada(asignacionDe(nueva))
    },
  })

  const alternar = (rol: RolConfigurable, codigo: string) =>
    setEditada((actual) => {
      const permisos = new Set(actual[rol])
      if (permisos.has(codigo)) permisos.delete(codigo)
      else permisos.add(codigo)
      return { ...actual, [rol]: permisos }
    })

  return (
    <>
      <p className="tarjeta">
        El Administrador tiene todos los permisos. Los marcados como &quot;Solo Administrador&quot; no se pueden dar a
        otros roles. {AVISO_SIGUIENTE_INICIO}
      </p>
      {matriz.modulos.map((modulo) => (
        <section key={modulo.codigo} aria-label={modulo.nombre}>
          <h2>{modulo.nombre}</h2>
          <div className="tabla-contenedor">
            <table className="tabla tabla-permisos">
              <thead>
                <tr>
                  <th>Permiso</th>
                  <th>Administrador</th>
                  <th>Supervisor</th>
                  <th>Cajero</th>
                </tr>
              </thead>
              <tbody>
                {modulo.permisos.map((permiso) => (
                  <tr key={permiso.codigo}>
                    <td>
                      {permiso.descripcion}
                      {permiso.exclusivoAdmin && <small className="pendiente"> · Solo Administrador</small>}
                    </td>
                    <td>
                      <input type="checkbox" checked disabled aria-label={`${permiso.descripcion}: Administrador`} />
                    </td>
                    {(['SUPERVISOR', 'CAJERO'] as const).map((rol) => (
                      <td key={rol}>
                        <input
                          type="checkbox"
                          checked={editada[rol].has(permiso.codigo)}
                          disabled={permiso.exclusivoAdmin || guardado.isPending}
                          aria-label={`${permiso.descripcion}: ${rol === 'SUPERVISOR' ? 'Supervisor' : 'Cajero'}`}
                          onChange={() => alternar(rol, permiso.codigo)}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
      <div className="acciones barra-guardar">
        <button type="button" disabled={cambiados.length === 0 || guardado.isPending} onClick={() => guardado.mutate()}>
          {guardado.isPending ? 'Guardando…' : 'Guardar cambios'}
        </button>
        <button
          type="button"
          disabled={cambiados.length === 0 || guardado.isPending}
          onClick={() => setEditada(guardada)}
        >
          Descartar
        </button>
      </div>
      {guardado.isSuccess && cambiados.length === 0 && (
        <p className="exito" role="status">
          Permisos guardados. {AVISO_SIGUIENTE_INICIO}
        </p>
      )}
      {guardado.error && (
        <p className="error" role="alert">
          {guardado.error.message}
        </p>
      )}
    </>
  )
}
