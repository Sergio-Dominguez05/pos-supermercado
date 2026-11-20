import { useQuery } from '@tanstack/react-query'
import { obtenerSesion, type UsuarioSesion } from '../../api/autenticacion'

export const CLAVE_SESION = ['sesion'] as const

/** Sesión del usuario. Se consulta una vez y se actualiza al iniciar sesión. */
export const useSesion = () =>
  useQuery({ queryKey: CLAVE_SESION, queryFn: obtenerSesion, staleTime: Infinity })

/** Pantalla a la que entra cada rol: el Cajero va directo a la venta (HU-01.02 crit. 1). */
export const destinoInicial = (rol: UsuarioSesion['rol']) => (rol === 'CAJERO' ? '/venta' : '/inicio')

/** HU-01.15 crit. 1: por qué se pide el cambio antes de continuar. */
export const avisoCredencialTemporal = (esPin: boolean) =>
  esPin
    ? 'Su PIN es temporal. Para continuar, cámbielo por uno que solo usted conozca.'
    : 'Su contraseña es temporal. Para continuar, cámbiela por una que solo usted conozca.'
