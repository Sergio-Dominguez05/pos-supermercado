import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { crearUsuario, type Rol } from '../../api/usuarios'
import { useSesion } from '../login/sesion'
import { CampoCredencial } from './CampoCredencial'
import { avisoCreacion, ROLES } from './formato'

/** HU-01.05: el Administrador crea usuarios con una contraseña o PIN temporal. */
export function NuevoUsuario() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Nuevo usuario</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/usuarios">Usuarios</Link> · Nuevo
      </p>
      {/* El servidor exige el permiso; esto solo evita mostrar un formulario que no se podrá usar. */}
      {usuario.permisos.includes('USUARIO_ADMINISTRAR') ? (
        <FormularioUsuario />
      ) : (
        <p className="tarjeta error">No tiene permiso para administrar usuarios.</p>
      )}
    </main>
  )
}

export function FormularioUsuario({ rolInicial = 'CAJERO' }: { rolInicial?: Rol }) {
  const [nombreCompleto, setNombreCompleto] = useState('')
  const [username, setUsername] = useState('')
  const [rol, setRol] = useState<Rol>(rolInicial)
  const [credencial, setCredencial] = useState('')
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()

  const creacion = useMutation({
    mutationFn: () => crearUsuario({ nombreCompleto, username, rol, credencialTemporal: credencial }),
    // Vuelve a la lista ya actualizada; el aviso de creación se muestra allí. Si falla, se queda aquí con el error.
    onSuccess: async (nuevo) => {
      await clienteConsultas.invalidateQueries({ queryKey: ['usuarios'] })
      navegar('/usuarios', { state: { aviso: avisoCreacion(nuevo) } })
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        creacion.mutate()
      }}
    >
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
        Nombre de usuario
        <input
          name="username"
          required
          minLength={3}
          maxLength={30}
          pattern="[A-Za-z0-9._\-]+"
          title="De 3 a 30 caracteres: letras, números, punto, guion o guion bajo"
          autoComplete="off"
          value={username}
          onChange={(evento) => setUsername(evento.target.value)}
        />
        <small>Se guarda en minúsculas; no distingue mayúsculas al iniciar sesión.</small>
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
      </label>
      <CampoCredencial rol={rol} valor={credencial} onChange={setCredencial} />

      {creacion.error && (
        <p className="error" role="alert">
          {creacion.error.message}
        </p>
      )}
      <button type="submit" disabled={creacion.isPending}>
        {creacion.isPending ? 'Guardando…' : 'Crear usuario'}
      </button>
    </form>
  )
}
