import type { FiltrosCierres } from '../../api/turnos'

/** El día (AAAA-MM-DD) de esa fecha en hora de Guatemala, aunque el navegador tenga otra zona configurada. */
export const diaEnGuatemala = (fecha: Date) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Guatemala' }).format(fecha)

/** HU-09.13, decisión de Sergio: la consulta abre con los últimos 7 días (hoy y los 6 anteriores). */
export const filtrosIniciales = (ahora: Date = new Date()): FiltrosCierres => ({
  desde: diaEnGuatemala(new Date(ahora.getTime() - 6 * 24 * 60 * 60 * 1000)),
  hasta: diaEnGuatemala(ahora),
  cajaId: '',
  cajeroId: '',
  soloConDiferencias: false,
})

export const rotuloCierres = (pagina: number, tamano: number, total: number, enPagina: number) =>
  total === 0
    ? 'No hay cierres con estos filtros.'
    : `Mostrando ${pagina * tamano + 1}–${pagina * tamano + enPagina} de ${total} ${total === 1 ? 'cierre' : 'cierres'}`

export const fechaYHora = (iso: string) =>
  new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Guatemala' })
