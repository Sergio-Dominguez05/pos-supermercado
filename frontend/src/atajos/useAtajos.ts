import { useEffect } from 'react'
import { accionDeTecla, type AccionCaja } from './atajos'

/**
 * El manejador de una acción. Recibe el evento por si necesita saber dónde está el foco; si devuelve `false`, la tecla
 * no era para él y sigue su comportamiento normal (por ejemplo, Supr borrando texto en un campo).
 */
export type Manejador = (evento: KeyboardEvent) => boolean | void

export type Manejadores = Partial<Record<AccionCaja, Manejador>>

/** Ejecuta la acción asociada a cada tecla mientras la pantalla esté montada. */
export function useAtajos(manejadores: Manejadores) {
  useEffect(() => {
    const alPresionar = (evento: KeyboardEvent) => {
      const accion = accionDeTecla(evento.key)
      const manejador = accion && manejadores[accion]
      if (manejador && manejador(evento) !== false) {
        evento.preventDefault()
      }
    }
    window.addEventListener('keydown', alPresionar)
    return () => window.removeEventListener('keydown', alPresionar)
  }, [manejadores])
}
