import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { iniciarSesion, iniciarSesionConPin } from '../../api/autenticacion'
import { CLAVE_SESION, destinoInicial, useSesion } from './sesion'

export type Modo = 'contrasena' | 'pin'

const MODOS: { valor: Modo; etiqueta: string }[] = [
  { valor: 'contrasena', etiqueta: 'Contraseña' },
  { valor: 'pin', etiqueta: 'PIN' },
]

// Cada caja recuerda la última pestaña usada; sin almacenamiento disponible se empieza en contraseña.
const CLAVE_MODO = 'pos.login.modo'

function modoGuardado(): Modo {
  try {
    return localStorage.getItem(CLAVE_MODO) === 'pin' ? 'pin' : 'contrasena'
  } catch {
    return 'contrasena'
  }
}

function guardarModo(modo: Modo) {
  try {
    localStorage.setItem(CLAVE_MODO, modo)
  } catch {
    // Sin almacenamiento: la próxima vez se empieza en contraseña.
  }
}

/** Inicio de sesión: contraseña para Administrador y Supervisor (HU-01.01), PIN para Cajero (HU-01.02). */
export function Login() {
  const { data: sesionActual } = useSesion()
  const [modo, setModo] = useState<Modo>(modoGuardado)

  if (sesionActual) return <Navigate to={destinoInicial(sesionActual.rol)} replace />

  return (
    <main className="pagina pagina-login">
      <h1>POS Supermercado</h1>
      <p className="subtitulo">Iniciar sesión</p>

      <div className="pestanas" role="tablist" aria-label="Tipo de credencial">
        {MODOS.map(({ valor, etiqueta }) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={modo === valor}
            onClick={() => {
              setModo(valor)
              guardarModo(valor)
            }}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      <FormularioLogin key={modo} modo={modo} />
    </main>
  )
}

export function FormularioLogin({ modo }: { modo: Modo }) {
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()
  const [username, setUsername] = useState('')
  const [credencial, setCredencial] = useState('')
  const esPin = modo === 'pin'

  const inicio = useMutation({
    mutationFn: () => (esPin ? iniciarSesionConPin(username, credencial) : iniciarSesion(username, credencial)),
    onSuccess: (usuario) => {
      clienteConsultas.setQueryData(CLAVE_SESION, usuario)
      navegar(destinoInicial(usuario.rol), { replace: true })
    },
    // El mensaje del servidor es genérico: no indica si falló el usuario o la credencial.
    onError: () => setCredencial(''),
  })

  return (
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
      {esPin ? (
        <label>
          PIN
          {/* Oculto como contraseña; inputMode abre el teclado numérico en pantallas táctiles (HU-01.02 crit. 3). */}
          <input
            type="password"
            name="pin"
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
        </label>
      ) : (
        <label>
          Contraseña
          <input
            type="password"
            name="contrasena"
            autoComplete="current-password"
            required
            maxLength={128}
            value={credencial}
            onChange={(evento) => setCredencial(evento.target.value)}
          />
        </label>
      )}
      {inicio.error && (
        <p className="error" role="alert">
          {inicio.error.message}
        </p>
      )}
      <button type="submit" disabled={inicio.isPending}>
        {inicio.isPending ? 'Ingresando…' : 'Iniciar sesión'}
      </button>
    </form>
  )
}
