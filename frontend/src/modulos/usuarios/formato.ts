import type { ResumenUsuario, Rol, UsuarioCreado } from '../../api/usuarios'

export const ROLES: { valor: Rol; nombre: string }[] = [
  { valor: 'ADMIN', nombre: 'Administrador' },
  { valor: 'SUPERVISOR', nombre: 'Supervisor' },
  { valor: 'CAJERO', nombre: 'Cajero' },
]

export const nombreDelRol = (rol: Rol) => ROLES.find((r) => r.valor === rol)?.nombre ?? rol

/**
 * Al pasar entre Cajero (PIN) y Administrador o Supervisor (contraseña) hay que asignar
 * una credencial temporal del nuevo tipo (HU-01.07 crit. 3).
 */
export const requiereCredencialNueva = (rolAnterior: Rol, rolNuevo: Rol) =>
  (rolAnterior === 'CAJERO') !== (rolNuevo === 'CAJERO')

// Avisos que la lista de usuarios muestra al volver de crear o editar (llegan en el estado de la navegación).

export const avisoCreacion = (usuario: UsuarioCreado) =>
  `Se creó el usuario ${usuario.username} (${nombreDelRol(usuario.rol)}), activo y con ` +
  `${usuario.rol === 'CAJERO' ? 'PIN' : 'contraseña'} temporal.`

export const avisoEdicion = (usuario: ResumenUsuario, rolCambio: boolean) =>
  `Se actualizó el usuario ${usuario.username}.` +
  (rolCambio ? ' El nuevo rol se aplica a partir de su siguiente inicio de sesión.' : '')

/**
 * Botón de estado de cada fila (HU-01.08). Nadie puede desactivar su propio usuario (crit. 4), así que en esa
 * fila no se ofrece; el servidor lo valida igual.
 */
export const accionDeEstado = (usuario: ResumenUsuario, usernameActual: string) =>
  !usuario.activo ? 'activar' : usuario.username === usernameActual ? null : 'desactivar'

export const confirmacionDesactivar = (usuario: ResumenUsuario) =>
  `¿Desactivar a ${usuario.username}? No podrá iniciar sesión hasta que lo vuelva a activar. ` +
  'Su historial se conserva.'

export const avisoEstado = (usuario: ResumenUsuario) =>
  `Se ${usuario.activo ? 'activó' : 'desactivó'} el usuario ${usuario.username}.`

// La hora se muestra en la de Guatemala aunque el navegador de la caja tenga otra zona configurada.
const FORMATO_FECHA = new Intl.DateTimeFormat('es-GT', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Guatemala',
})

export const formatearUltimoAcceso = (iso: string | null) => (iso ? FORMATO_FECHA.format(new Date(iso)) : 'Nunca')
