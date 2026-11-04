import { useQuery } from '@tanstack/react-query'
import { obtenerSesion } from '../../api/autenticacion'

export const CLAVE_SESION = ['sesion'] as const

/** Sesión del usuario. Se consulta una vez y se actualiza al iniciar sesión. */
export const useSesion = () =>
  useQuery({ queryKey: CLAVE_SESION, queryFn: obtenerSesion, staleTime: Infinity })
