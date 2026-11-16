import type { Turno } from '../../api/turnos'

// Textos del turno de caja (EP-09), separados de los componentes para probarlos sin renderizar.

/** Hora en Guatemala, por ejemplo "08:05". */
export const horaDe = (iso: string) =>
  new Date(iso).toLocaleTimeString('es-GT', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'America/Guatemala' })

export const pedidoAbrirTurno = (cajaNombre: string) =>
  `${cajaNombre} está cerrada. Abra su turno para empezar a vender.`

export const turnoAbierto = (turno: Turno) => `Su turno en ${turno.cajaNombre} está abierto desde las ${horaDe(turno.abiertoEn)}.`

export const turnoDeOtroUsuario = (turno: Turno) =>
  `${turno.cajaNombre} tiene abierto el turno de ${turno.usuarioNombre}. Primero se debe cerrar ese turno.`
