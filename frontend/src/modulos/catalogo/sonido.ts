/**
 * Aviso sonoro corto (HU-03.08 crit. 1): dos tonos graves, distintos del pitido del lector, para que el cajero note
 * que el código no existe sin mirar la pantalla. Usa Web Audio, sin archivos de sonido. Si el navegador no lo
 * permite, no hace nada: el aviso visible sigue apareciendo.
 */
export function avisoSonoro() {
  try {
    const Contexto = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Contexto) return
    const contexto = new Contexto()
    const tonos = [440, 330]
    tonos.forEach((frecuencia, indice) => {
      const oscilador = contexto.createOscillator()
      const volumen = contexto.createGain()
      const inicio = contexto.currentTime + indice * 0.18
      oscilador.type = 'square'
      oscilador.frequency.value = frecuencia
      volumen.gain.setValueAtTime(0.15, inicio)
      volumen.gain.exponentialRampToValueAtTime(0.001, inicio + 0.16)
      oscilador.connect(volumen).connect(contexto.destination)
      oscilador.start(inicio)
      oscilador.stop(inicio + 0.16)
    })
    setTimeout(() => void contexto.close(), 600)
  } catch {
    // Sin audio disponible: basta con el aviso visible.
  }
}
