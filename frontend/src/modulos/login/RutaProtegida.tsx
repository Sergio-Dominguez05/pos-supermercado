import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { BarraSesion } from './BarraSesion'
import { useSesion } from './sesion'

/** Muestra el contenido solo con una sesión iniciada; si no, lleva al inicio de sesión. */
export function RutaProtegida({ children }: { children: ReactNode }) {
  const { data: usuario, error, isPending } = useSesion()

  if (isPending) return <main className="pagina">Verificando la sesión…</main>
  if (error) return <main className="pagina error">Sin conexión con el backend: {error.message}</main>
  if (!usuario) return <Navigate to="/login" replace />
  return (
    <>
      <BarraSesion usuario={usuario} />
      {children}
    </>
  )
}
