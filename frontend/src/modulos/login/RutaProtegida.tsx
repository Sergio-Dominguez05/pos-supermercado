import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { BarraSesion } from './BarraSesion'
import { useSesion } from './sesion'

/** La única pantalla disponible mientras la contraseña o el PIN sea temporal (HU-01.15). */
export const RUTA_CAMBIO_CREDENCIAL = '/credencial'

/**
 * Muestra el contenido solo con una sesión iniciada; si no, lleva al inicio de sesión. HU-01.15 crit. 1 y 2: con una
 * credencial temporal, cualquier pantalla lleva al cambio obligatorio (el servidor también rechaza todo lo demás).
 */
export function RutaProtegida({ children }: { children: ReactNode }) {
  const { data: usuario, error, isPending } = useSesion()
  const { pathname } = useLocation()

  if (isPending) return <main className="pagina">Verificando la sesión…</main>
  if (error) return <main className="pagina error">Sin conexión con el backend: {error.message}</main>
  if (!usuario) return <Navigate to="/login" replace />
  if (usuario.credencialTemporal && pathname !== RUTA_CAMBIO_CREDENCIAL)
    return <Navigate to={RUTA_CAMBIO_CREDENCIAL} replace />
  return (
    <>
      <BarraSesion usuario={usuario} />
      {children}
    </>
  )
}
