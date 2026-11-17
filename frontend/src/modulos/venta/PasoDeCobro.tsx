import type { Venta } from '../../api/ventas'
import { formatoQuetzales } from '../catalogo/textos'

/**
 * HU-06.15: el cajero terminó de registrar productos y pasó al cobro. Muestra el total a cobrar (el que se ingresa en
 * la terminal bancaria, HU-07.03 crit. 1) y permite regresar a la venta para agregar o quitar productos (crit. 4):
 * el botón recibe el foco y Escape hace lo mismo. El registro del pago con tarjeta llega con HU-07.03 (#60).
 */
export function PasoDeCobro({ venta, onRegresar }: { venta: Venta; onRegresar: () => void }) {
  return (
    <section
      className="cobro"
      aria-label="Cobro"
      onKeyDown={(evento) => {
        if (evento.key === 'Escape') onRegresar()
      }}
    >
      <h3>Cobro con tarjeta</h3>
      <dl className="totales" aria-label="Total a cobrar">
        <dt>Artículos</dt>
        <dd>{venta.articulos}</dd>
        <dt>Total a cobrar</dt>
        <dd className="total">{formatoQuetzales(venta.total)}</dd>
      </dl>
      <p className="pendiente">El registro del pago con tarjeta estará disponible con HU-07.03 (#60).</p>
      <div className="acciones">
        <button type="button" data-control="regresar-a-la-venta" autoFocus onClick={onRegresar}>
          Regresar a la venta <kbd>Esc</kbd>
        </button>
      </div>
    </section>
  )
}
