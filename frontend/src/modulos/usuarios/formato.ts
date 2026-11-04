import type { Rol } from '../../api/usuarios'

export const ROLES: { valor: Rol; nombre: string }[] = [
  { valor: 'ADMIN', nombre: 'Administrador' },
  { valor: 'SUPERVISOR', nombre: 'Supervisor' },
  { valor: 'CAJERO', nombre: 'Cajero' },
]

export const nombreDelRol = (rol: Rol) => ROLES.find((r) => r.valor === rol)?.nombre ?? rol

// La hora se muestra en la de Guatemala aunque el navegador de la caja tenga otra zona configurada.
const FORMATO_FECHA = new Intl.DateTimeFormat('es-GT', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Guatemala',
})

export const formatearUltimoAcceso = (iso: string | null) => (iso ? FORMATO_FECHA.format(new Date(iso)) : 'Nunca')
