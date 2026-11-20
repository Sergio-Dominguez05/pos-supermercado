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

// Cierre de caja (HU-09.08 a HU-09.10).

/** HU-09.08 crit. 3: la diferencia es el cierre de lote menos lo registrado, en centavos para no redondear mal. */
export const diferenciaDeCierre = (totalLote: number, totalRegistrado: number) =>
  (Math.round(totalLote * 100) - Math.round(totalRegistrado * 100)) / 100

/** Un total de lote en quetzales: cero o más, con hasta dos decimales. */
export const totalLoteValido = (texto: string) => /^\d+(\.\d{1,2})?$/.test(texto.trim())

export const confirmacionCierre = (cajaNombre: string, diferencia: number) =>
  `¿Cerrar la caja ${cajaNombre}? ` +
  (diferencia === 0 ? 'El cierre de lote coincide con lo registrado. ' : '') +
  'Después de cerrarla no se puede modificar el turno.'
