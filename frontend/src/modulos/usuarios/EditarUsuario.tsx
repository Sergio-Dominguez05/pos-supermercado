import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { claveUsuario, editarUsuario, obtenerUsuario, type ResumenUsuario, type Rol } from '../../api/usuarios'
import { useSesion } from '../login/sesion'
import { CampoCredencial } from './CampoCredencial'
import { avisoEdicion, nombreDelRol, requiereCredencialNueva, ROLES } from './formato'

/** HU-01.07: el Administrador cambia el nombre completo y el rol de un usuario. */
export function EditarUsuario() {
  const { data: sesion } = useSesion()
  const id = Number(useParams().id)
  if (!sesion) return null

  return (
    <main className="pagina">
      <h1>Editar usuario</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/usuarios">Usuarios</Link> · Editar
      </p>
      {/* El servidor exige el permiso; esto solo evita mostrar un formulario que no se podrá usar. */}
      {sesion.permisos.includes('USUARIO_ADMINISTRAR') ? (
        <CargaUsuario id={id} />
      ) : (
        <p className="tarjeta error">No tiene permiso para administrar usuarios.</p>
      )}
    </main>
  )
}

function CargaUsuario({ id }: { id: number }) {
  const { data: usuario, error, isPending } = useQuery({
    queryKey: claveUsuario(id),
    queryFn: () => obtenerUsuario(id),
  })

  if (isPending) return <p className="tarjeta">Cargando usuario…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioEdicion usuario={usuario} />
}

export function FormularioEdicion({ usuario }: { usuario: ResumenUsuario }) {
  const [nombreCompleto, setNombreCompleto] = useState(usuario.nombreCompleto)
  const [rol, setRol] = useState<Rol>(usuario.rol)
  const [credencial, setCredencial] = useState('')
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()
  const pideCredencial = requiereCredencialNueva(usuario.rol, rol)

  const edicion = useMutation({
    mutationFn: () =>
      editarUsuario(usuario.id, {
        nombreCompleto,
        rol,
        ...(pideCredencial ? { credencialTemporal: credencial } : {}),
      }),
    // Vuelve a la lista ya actualizada con el aviso. Si falla, se queda aquí con el error.
    onSuccess: async (editado) => {
      await clienteConsultas.invalidateQueries({ queryKey: ['usuarios'] })
      navegar('/usuarios', { state: { aviso: avisoEdicion(editado, editado.rol !== usuario.rol) } })
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        edicion.mutate()
      }}
    >
      <label>
        Nombre de usuario
        <input name="username" value={usuario.username} readOnly disabled />
        <small>No se puede modificar, para mantener la trazabilidad en la bitácora.</small>
      </label>
      <label>
        Nombre completo
        <input
          name="nombreCompleto"
          required
          maxLength={100}
          value={nombreCompleto}
          onChange={(evento) => setNombreCompleto(evento.target.value)}
        />
      </label>
      <label>
        Rol
        <select
          name="rol"
          value={rol}
          onChange={(evento) => {
            setRol(evento.target.value as Rol)
            setCredencial('')
          }}
        >
          {ROLES.map(({ valor, nombre }) => (
            <option key={valor} value={valor}>
              {nombre}
            </option>
          ))}
        </select>
        {rol !== usuario.rol && (
          <small>
            Cambia de {nombreDelRol(usuario.rol)} a {nombreDelRol(rol)}. Los nuevos permisos se aplican a partir de su
            siguiente inicio de sesión.
          </small>
        )}
      </label>
      {pideCredencial && (
        <>
          <p className="aviso">
            Como {rol === 'CAJERO' ? 'Cajero entrará con PIN' : `${nombreDelRol(rol)} entrará con contraseña`}, asigne
            {rol === 'CAJERO' ? ' un PIN temporal' : ' una contraseña temporal'}.
          </p>
          <CampoCredencial rol={rol} valor={credencial} onChange={setCredencial} />
        </>
      )}

      {edicion.error && (
        <p className="error" role="alert">
          {edicion.error.message}
        </p>
      )}
      <button type="submit" disabled={edicion.isPending}>
        {edicion.isPending ? 'Guardando…' : 'Guardar cambios'}
      </button>
    </form>
  )
}
