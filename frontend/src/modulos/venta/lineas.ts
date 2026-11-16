import type { LineaVenta, Venta } from '../../api/ventas'

/** La línea que cambió con el último escaneo: la nueva, o la que aumentó su cantidad (HU-06.02 crit. 2 y 3). */
export function lineaAgregada(anterior: Venta | undefined, venta: Venta): LineaVenta | undefined {
  const cantidades = new Map(anterior?.lineas.map((linea) => [linea.id, linea.cantidad]))
  return (
    venta.lineas.find((linea) => cantidades.get(linea.id) !== linea.cantidad) ?? venta.lineas[venta.lineas.length - 1]
  )
}
