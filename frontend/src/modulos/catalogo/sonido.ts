type Tono = { frecuencia: number; forma: OscillatorType }

/** Toca los tonos uno tras otro con Web Audio, sin archivos de sonido. Si el navegador no lo permite, no hace nada. */
function tocar(tonos: Tono[], duracion: number) {
  try {
    const Contexto = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Contexto) return
    const contexto = new Contexto()
    tonos.forEach(({ frecuencia, forma }, indice) => {
      const oscilador = contexto.createOscillator()
      const volumen = contexto.createGain()
      const inicio = contexto.currentTime + indice * (duracion + 0.02)
      oscilador.type = forma
      oscilador.frequency.value = frecuencia
      volumen.gain.setValueAtTime(0.15, inicio)
      volumen.gain.exponentialRampToValueAtTime(0.001, inicio + duracion)
      oscilador.connect(volumen).connect(contexto.destination)
      oscilador.start(inicio)
      oscilador.stop(inicio + duracion)
    })
    setTimeout(() => void contexto.close(), 600)
  } catch {
    // Sin audio disponible: basta con el aviso visible.
  }
}

/**
 * Aviso sonoro corto (HU-03.08 crit. 1): dos tonos graves, distintos del pitido del lector, para que el cajero note
 * que el código no existe sin mirar la pantalla. Si el navegador no lo permite, el aviso visible sigue apareciendo.
 */
export function avisoSonoro() {
  tocar(
    [
      { frecuencia: 440, forma: 'square' },
      { frecuencia: 330, forma: 'square' },
    ],
    0.16,
  )
}

/** Confirmación sonora al agregar un producto a la venta (HU-06.02 crit. 3): un tono agudo y breve. */
export function confirmacionSonora() {
  tocar([{ frecuencia: 880, forma: 'sine' }], 0.08)
}
