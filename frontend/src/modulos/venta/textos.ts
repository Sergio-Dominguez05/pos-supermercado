import type { Venta } from '../../api/ventas'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-04.03 crit. 2: montos del desglose por línea con 6 decimales, sin redondear a 2. */
export const formatoDesglose = (monto: number) => `Q${monto.toFixed(6)}`

/** HU-06.14 crit. 1: el sistema pide confirmación antes de cancelar. */
export const confirmacionCancelarVenta = (venta: Venta) =>
  `¿Cancelar la venta N.º ${venta.numero}? Se quitarán ${venta.articulos} ` +
  `${venta.articulos === 1 ? 'artículo' : 'artículos'} (${formatoQuetzales(venta.total)}) y no se podrá recuperar.`
