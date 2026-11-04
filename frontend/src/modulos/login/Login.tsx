import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { iniciarSesion } from '../../api/autenticacion'
import { CLAVE_SESION, useSesion } from './sesion'

/** HU-01.01: inicio de sesión con usuario y contraseña para Administrador y Supervisor. */
export function Login() {
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()
  const { data: sesionActual } = useSesion()
  const [username, setUsername] = useState('')
  const [contrasena, setContrasena] = useState('')

  const inicio = useMutation({
    mutationFn: () => iniciarSesion(username, contrasena),
    onSuccess: (usuario) => {
      clienteConsultas.setQueryData(CLAVE_SESION, usuario)
      navegar('/inicio', { replace: true })
    },
    // El mensaje del servidor es genérico: no indica si falló el usuario o la contraseña.
    onError: () => setContrasena(''),
  })

  if (sesionActual) return <Navigate to="/inicio" replace />

  return (
    <main className="pagina pagina-login">
      <h1>POS Supermercado</h1>
      <p className="subtitulo">Iniciar sesión</p>

      <form
        className="tarjeta formulario"
        onSubmit={(evento) => {
          evento.preventDefault()
          inicio.mutate()
        }}
      >
        <label>
          Usuario
          <input
            name="username"
            autoComplete="username"
            autoFocus
            required
            maxLength={100}
            value={username}
            onChange={(evento) => setUsername(evento.target.value)}
          />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            name="contrasena"
            autoComplete="current-password"
            required
            maxLength={128}
            value={contrasena}
            onChange={(evento) => setContrasena(evento.target.value)}
          />
        </label>
        {inicio.error && (
          <p className="error" role="alert">
            {inicio.error.message}
          </p>
        )}
        <button type="submit" disabled={inicio.isPending}>
          {inicio.isPending ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>
    </main>
  )
}
