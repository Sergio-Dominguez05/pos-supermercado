import type { Venta } from '../../api/ventas'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-06.14 crit. 1: el sistema pide confirmación antes de cancelar. */
export const confirmacionCancelarVenta = (venta: Venta) =>
  `¿Cancelar la venta N.º ${venta.numero}? Se quitarán ${venta.articulos} ` +
  `${venta.articulos === 1 ? 'artículo' : 'artículos'} (${formatoQuetzales(venta.total)}) y no se podrá recuperar.`
