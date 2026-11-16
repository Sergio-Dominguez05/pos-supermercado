import { useQuery } from '@tanstack/react-query'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, turnoActual, type Turno } from '../../api/turnos'

/** Si el usuario opera una caja: tiene el permiso y el equipo está vinculado a una (HU-12.05). */
export const operaCaja = (usuario: UsuarioSesion | null | undefined) =>
  !!usuario?.cajaNombre && usuario.permisos.includes('CAJA_OPERAR_PROPIA')

/** Turno abierto en la caja de este equipo (HU-09.01). Solo se consulta si el usuario opera una caja. */
export const useTurnoActual = (usuario: UsuarioSesion | null | undefined) =>
  useQuery({ queryKey: CLAVE_TURNO_ACTUAL, queryFn: turnoActual, enabled: operaCaja(usuario) })

/**
 * HU-09.02 crit. 1: solo se vende con el turno propio abierto. Es una comodidad de la pantalla; el servidor lo vuelve
 * a exigir en cada venta (`GestionTurnos.exigirTurnoAbierto`).
 */
export const puedeVender = (usuario: UsuarioSesion | null | undefined, turno: Turno | null | undefined) =>
  operaCaja(usuario) && !!usuario?.permisos.includes('VENTA_REGISTRAR') && turno?.propio === true
