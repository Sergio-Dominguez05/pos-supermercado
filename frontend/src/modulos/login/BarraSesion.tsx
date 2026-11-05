import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { cerrarSesion, type UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_SESION } from './sesion'

/** Quién tiene la sesión abierta y el botón para cerrarla (HU-01.16), en todas las pantallas protegidas. */
export function BarraSesion({ usuario }: { usuario: UsuarioSesion }) {
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()

  const salida = useMutation({
    mutationFn: cerrarSesion,
    onSuccess: () => {
      // Nada de lo consultado con esta sesión debe quedar visible para el siguiente usuario.
      clienteConsultas.clear()
      clienteConsultas.setQueryData(CLAVE_SESION, null)
      navegar('/login', { replace: true })
    },
  })

  return (
    <header className="barra-sesion">
      <span>
        {usuario.nombreCompleto} · {usuario.rolNombre}
      </span>
      {/* Por ejemplo, con una venta en curso: el servidor pide finalizarla, suspenderla o cancelarla. */}
      {salida.error && (
        <span className="error" role="alert">
          {salida.error.message}
        </span>
      )}
      <button type="button" className="boton-texto" disabled={salida.isPending} onClick={() => salida.mutate()}>
        {salida.isPending ? 'Cerrando…' : 'Cerrar sesión'}
      </button>
    </header>
  )
}
