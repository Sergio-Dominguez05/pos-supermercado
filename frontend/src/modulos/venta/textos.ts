import type { LineaVenta, Venta } from '../../api/ventas'
import { horaDe } from '../caja/textos'
import { formatoQuetzales } from '../catalogo/textos'

/** HU-04.03 crit. 2: montos del desglose por línea con 6 decimales, sin redondear a 2. */
export const formatoDesglose = (monto: number) => `Q${monto.toFixed(6)}`

/** HU-06.14 crit. 1: el sistema pide confirmación antes de cancelar. */
export const confirmacionCancelarVenta = (venta: Venta) =>
  `¿Cancelar la venta N.º ${venta.numero}? Se quitarán ${venta.articulos} ` +
  `${venta.articulos === 1 ? 'artículo' : 'artículos'} (${formatoQuetzales(venta.total)}) y no se podrá recuperar.`

/** HU-06.07: se confirma antes de quitar una línea, para no perder un producto por un clic equivocado. */
export const confirmacionQuitarLinea = (linea: LineaVenta) =>
  `¿Quitar ${linea.cantidad} x ${linea.descripcion} (${formatoQuetzales(linea.subtotalConIva)}) de la venta?`

/** HU-06.12 crit. 1: qué venta quedó en espera; la pantalla ya está lista para la siguiente. */
export const avisoVentaSuspendida = (venta: Venta) =>
  `Venta N.º ${venta.numero} en espera: ${venta.articulos} ${venta.articulos === 1 ? 'artículo' : 'artículos'} ` +
  `(${formatoQuetzales(venta.total)}). Ya puede atender al siguiente cliente.`

/** HU-06.13 crit. 1: cada venta en espera con su número, hora, artículos y total. */
export const descripcionSuspendida = (venta: Venta) =>
  `N.º ${venta.numero} · ${venta.suspendidaEn ? horaDe(venta.suspendidaEn) : '--:--'} · ${venta.articulos} ` +
  `${venta.articulos === 1 ? 'artículo' : 'artículos'} · ${formatoQuetzales(venta.total)}`

/** HU-06.13: el botón que abre la lista muestra cuántas ventas hay en espera. */
export const rotuloSuspendidas = (cantidad: number) => `Ventas en espera (${cantidad})`

/** HU-06.13 crit. 2: la venta retomada ya está en curso con sus productos. */
export const avisoVentaRetomada = (venta: Venta) =>
  `Venta N.º ${venta.numero} retomada: ${venta.articulos} ${venta.articulos === 1 ? 'artículo' : 'artículos'} ` +
  `(${formatoQuetzales(venta.total)}).`
