import type { Venta } from '../../api/ventas'
import type { AccionCaja } from '../../atajos/atajos'

/**
 * HU-06.17 crit. 1: las acciones del mapa de atajos que la pantalla de venta ya atiende. Las demás están en
 * `ACCIONES_PENDIENTES` con la historia que las trae; entre las dos cubren todo el mapa (`teclado.test.ts`).
 */
export const ACCIONES_DE_LA_VENTA = [
  'ayuda',
  'buscarProducto',
  'cambiarCantidad',
  'eliminarLinea',
  'asignarCliente',
  'suspenderVenta',
  'retomarVenta',
  'cancelarVenta',
  'cobrar',
] as const satisfies readonly AccionCaja[]

export type AccionDeLaVenta = (typeof ACCIONES_DE_LA_VENTA)[number]

/**
 * HU-06.17: la línea sobre la que actúan F4 (cambiar cantidad) y Supr (eliminar). Después de cada cambio es la línea
 * que cambió (la recién escaneada, la que aumentó o la que se editó); si nada cambió, se conserva la que el cajero
 * eligió con las flechas o con el mouse; si esa ya no está (se quitó), la última.
 */
export function lineaSeleccionada(anterior: Venta | undefined, venta: Venta, actual: number | undefined) {
  const cantidades = new Map(anterior?.lineas.map((linea) => [linea.id, linea.cantidad]))
  const cambiada = anterior && venta.lineas.find((linea) => cantidades.get(linea.id) !== linea.cantidad)
  if (cambiada) return cambiada.id
  if (venta.lineas.some((linea) => linea.id === actual)) return actual
  return venta.lineas.at(-1)?.id
}

/** Flechas ↑ y ↓: la línea anterior o la siguiente, sin salirse de la lista. */
export function moverSeleccion(venta: Venta, actual: number | undefined, paso: -1 | 1) {
  const ids = venta.lineas.map((linea) => linea.id)
  if (ids.length === 0) return undefined
  const posicion = ids.indexOf(actual ?? -1)
  if (posicion < 0) return ids.at(-1)
  return ids[Math.min(ids.length - 1, Math.max(0, posicion + paso))]
}

/**
 * Supr elimina la línea seleccionada, salvo que el cajero esté editando texto: en la cantidad de una línea, en la
 * búsqueda o en un código a medio escribir, Supr borra caracteres como siempre. En el campo del código vale cuando
 * está vacío o con todo el texto seleccionado, que es como queda después de cada escaneo.
 */
export function supresionEsParaLaLinea(destino: EventTarget | null) {
  const campo = destino as Partial<HTMLInputElement> | null
  if (campo?.tagName !== 'INPUT') return true
  if (campo.name !== 'codigo') return false
  const largo = campo.value?.length ?? 0
  return largo === 0 || (campo.selectionStart === 0 && campo.selectionEnd === largo)
}
