import { useState } from 'react'
import type { Producto } from '../../api/productos'
import { BuscadorPorCodigo, ResultadoProducto } from '../catalogo/BuscadorPorCodigo'
import { BuscadorPorDescripcion } from '../catalogo/BuscadorPorDescripcion'

/**
 * HU-03.10 Consultar precio sin vender (F3, decisión de Sergio: un panel sobre la venta).
 * - Crit. 1: se escanea o se escribe el código, o se busca por descripción, y se ven la descripción y el precio. Se
 *   pueden consultar varios productos seguidos.
 * - Crit. 2: nada de lo consultado se agrega a la venta en curso (los buscadores no reciben `onEncontrado`).
 * - Crit. 3: Escape o F3 vuelven a la venta.
 */
export function ConsultaPrecio({ onCerrar }: { onCerrar: () => void }) {
  const [elegido, setElegido] = useState<Producto>()
  return (
    <section
      className="tarjeta consulta-precio"
      aria-label="Consultar precio"
      onKeyDown={(evento) => {
        if (evento.key !== 'Escape') return
        // En la búsqueda por descripción, el primer Escape limpia lo escrito (HU-03.07 crit. 4); con el campo vacío,
        // vuelve a la venta.
        const campo = evento.target
        if (campo instanceof HTMLInputElement && campo.name === 'descripcion' && campo.value !== '') return
        evento.preventDefault()
        onCerrar()
      }}
    >
      <h2>
        Consultar precio <kbd>F3</kbd>
      </h2>
      <p className="pendiente">
        Lo que consulte aquí no se agrega a la venta. <kbd>Esc</kbd> o <kbd>F3</kbd> para volver a la venta.
      </p>
      <BuscadorPorCodigo />
      <BuscadorPorDescripcion onSeleccionado={setElegido} />
      {elegido && <ResultadoProducto producto={elegido} />}
      <button type="button" onClick={onCerrar}>
        Volver a la venta
      </button>
    </section>
  )
}
