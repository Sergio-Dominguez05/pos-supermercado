import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { crearUsuario, type Rol, type UsuarioCreado } from '../../api/usuarios'
import { useSesion } from '../login/sesion'

const ROLES:{ valor: Rol; nombre: string }[] = [
  { valor: 'ADMIN', nombre: 'Administrador' },
  { valor: 'SUPERVISOR', nombre: 'Supervisor' },
  { valor: 'CAJERO', nombre: 'Cajero' },
]

const nombreDelRol = (rol: Rol) => ROLES.find((r) => r.valor === rol)?.nombre ?? rol

/** HU-01.05: el Administrador crea usuarios con una contraseña o PIN temporal. */
export function NuevoUsuario() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Nuevo usuario</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Usuarios
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
  const [mostrar, setMostrar] = useState(false)
  const [creado, setCreado] = useState<UsuarioCreado>()
  const esPin = rol === 'CAJERO'

  const creacion = useMutation({
    mutationFn: () => crearUsuario({ nombreCompleto, username, rol, credencialTemporal: credencial }),
    onSuccess: (nuevo) => {
      setCreado(nuevo)
      setNombreCompleto('')
      setUsername('')
      setCredencial('')
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        setCreado(undefined)
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
      {esPin ? (
        <label>
          PIN temporal
          <input
            type={mostrar ? 'text' : 'password'}
            name="credencialTemporal"
            inputMode="numeric"
            pattern="\d{4,6}"
            title="De 4 a 6 dígitos"
            autoComplete="off"
            required
            minLength={4}
            maxLength={6}
            value={credencial}
            onChange={(evento) => setCredencial(evento.target.value.replace(/\D/g, ''))}
          />
          <small>Numérico, de 4 a 6 dígitos.</small>
        </label>
      ) : (
        <label>
          Contraseña temporal
          <input
            type={mostrar ? 'text' : 'password'}
            name="credencialTemporal"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={128}
            value={credencial}
            onChange={(evento) => setCredencial(evento.target.value)}
          />
          <small>Al menos 8 caracteres, con letras y números.</small>
        </label>
      )}
      {/* Quien crea el usuario debe entregarle la credencial temporal: puede revisarla antes de guardar. */}
      <label className="casilla">
        <input type="checkbox" checked={mostrar} onChange={(evento) => setMostrar(evento.target.checked)} />
        Mostrar {esPin ? 'el PIN' : 'la contraseña'}
      </label>

      {creacion.error && (
        <p className="error" role="alert">
          {creacion.error.message}
        </p>
      )}
      {creado && (
        <p className="exito" role="status">
          Se creó el usuario <strong>{creado.username}</strong> ({nombreDelRol(creado.rol)}), activo y con{' '}
          {creado.rol === 'CAJERO' ? 'PIN' : 'contraseña'} temporal.
        </p>
      )}
      <button type="submit" disabled={creacion.isPending}>
        {creacion.isPending ? 'Guardando…' : 'Crear usuario'}
      </button>
    </form>
  )
}
