import { ACCIONES_PENDIENTES, ATAJOS, rotuloDeTecla } from '../../atajos/atajos'

/**
 * HU-06.17 crit. 2: ayuda rápida con los atajos de la caja, que se abre y se cierra con F1. Muestra el mapa central
 * completo (Architecture.md §7.6); las acciones que todavía no existen aparecen con la historia que las trae.
 */
export function AyudaAtajos({ onCerrar }: { onCerrar: () => void }) {
  return (
    <section className="tarjeta ayuda-atajos" aria-label="Atajos de teclado">
      <h2>Atajos de teclado</h2>
      <ul className="atajos">
        <li>
          <kbd>6*</kbd> antes del código: varias unidades
        </li>
        <li>
          <kbd>↑</kbd> <kbd>↓</kbd> en el código: elegir línea
        </li>
        {ATAJOS.map((atajo) => {
          const pendiente = ACCIONES_PENDIENTES[atajo.accion]
          return (
            <li key={atajo.tecla} className={pendiente ? 'pendiente' : undefined}>
              <kbd>{rotuloDeTecla(atajo.tecla)}</kbd> {atajo.descripcion}
              {pendiente && <small> (llega con {pendiente})</small>}
            </li>
          )
        })}
      </ul>
      <button type="button" onClick={onCerrar}>
        Cerrar <kbd>F1</kbd>
      </button>
    </section>
  )
}
