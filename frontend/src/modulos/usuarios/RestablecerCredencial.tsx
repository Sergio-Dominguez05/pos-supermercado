import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { claveUsuario, obtenerUsuario, restablecerCredencial, type ResumenUsuario } from '../../api/usuarios'
import { useSesion } from '../login/sesion'
import { CampoCredencial } from './CampoCredencial'
import { avisoRestablecimiento } from './formato'

/** HU-01.14: el Administrador asigna una contraseña o PIN temporal a un usuario que lo olvidó. */
export function RestablecerCredencial() {
  const { data: sesion } = useSesion()
  const id = Number(useParams().id)
  if (!sesion) return null

  return (
    <main className="pagina">
      <h1>Restablecer acceso</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/usuarios">Usuarios</Link> · Restablecer
      </p>
      {/* Solo el Administrador (crit. 2). El servidor exige el permiso; esto evita mostrar un formulario inútil. */}
      {sesion.permisos.includes('CREDENCIAL_RESTABLECER') ? (
        <CargaUsuario id={id} />
      ) : (
        <p className="tarjeta error">No tiene permiso para restablecer contraseñas ni PIN.</p>
      )}
    </main>
  )
}

function CargaUsuario({ id }: { id: number }) {
  const { data: usuario, error, isPending } = useQuery({ queryKey: claveUsuario(id), queryFn: () => obtenerUsuario(id) })

  if (isPending) return <p className="tarjeta">Cargando usuario…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioRestablecer usuario={usuario} />
}

export function FormularioRestablecer({ usuario }: { usuario: ResumenUsuario }) {
  const [credencial, setCredencial] = useState('')
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()
  const esPin = usuario.rol === 'CAJERO'

  const restablecimiento = useMutation({
    mutationFn: () => restablecerCredencial(usuario.id, credencial),
    onSuccess: async ({ tipo }) => {
      await clienteConsultas.invalidateQueries({ queryKey: ['usuarios'] })
      navegar('/usuarios', { state: { aviso: avisoRestablecimiento(usuario.username, tipo) } })
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        restablecimiento.mutate()
      }}
    >
      <dl>
        <dt>Usuario</dt>
        <dd>
          {usuario.nombreCompleto} ({usuario.username})
        </dd>
        <dt>Rol</dt>
        <dd>{usuario.rolNombre}</dd>
      </dl>
      {/* Crit. 3: la credencial actual nunca se muestra; el sistema ni siquiera la conoce, solo su hash. */}
      <p className="aviso">
        {esPin
          ? 'Asigne un PIN temporal y entrégueselo al usuario. Su PIN actual deja de funcionar.'
          : 'Asigne una contraseña temporal y entréguesela al usuario. Su contraseña actual deja de funcionar.'}
      </p>
      <CampoCredencial rol={usuario.rol} valor={credencial} onChange={setCredencial} />

      {restablecimiento.error && (
        <p className="error" role="alert">
          {restablecimiento.error.message}
        </p>
      )}
      <button type="submit" disabled={restablecimiento.isPending}>
        {restablecimiento.isPending ? 'Guardando…' : `Restablecer ${esPin ? 'PIN' : 'contraseña'}`}
      </button>
    </form>
  )
}
