import { ErrorDeApi } from '../../api/cliente'

/** Lo que escribió el cajero en el campo del código, separado en la cantidad y el código (HU-06.04 crit. 1). */
export type Escaneo = { cantidad?: number; codigo: string } | { error: string }

export const CANTIDAD_NO_VALIDA = 'La cantidad debe ser un número entero mayor que cero.'
export const FALTA_EL_CODIGO = 'Escanee o escriba el código del producto después de la cantidad.'
export const CANTIDAD_SIN_CONFIRMAR = 'La cantidad no se confirmó: la venta quedó sin cambios.'

/**
 * HU-06.04 crit. 1: "6*" antes del código indica la cantidad. El cajero escribe "6*" y luego escanea, así que el
 * campo termina con "6*7401234567890" cuando el lector envía el Enter. Sin asterisco es una unidad (HU-06.02).
 * Crit. 3: la cantidad debe ser un entero mayor que cero; "0*", "2.5*" o "x*" se rechazan sin consultar al servidor.
 */
export function separarCantidad(texto: string): Escaneo {
  const asterisco = texto.indexOf('*')
  if (asterisco < 0) return { codigo: texto }
  const cantidad = texto.slice(0, asterisco).trim()
  const codigo = texto.slice(asterisco + 1).trim()
  if (!/^\d+$/.test(cantidad) || Number(cantidad) < 1) return { error: CANTIDAD_NO_VALIDA }
  if (!codigo) return { error: FALTA_EL_CODIGO }
  return { cantidad: Number(cantidad), codigo }
}

/** Crit. 3 al editar la cantidad de una línea: un entero mayor que cero, o `undefined` si no lo es. */
export function cantidadValida(texto: string): number | undefined {
  const limpio = texto.trim()
  return /^\d+$/.test(limpio) && Number(limpio) >= 1 ? Number(limpio) : undefined
}

/**
 * HU-06.04 crit. 4: si el servidor responde que la cantidad supera el límite configurado, se la muestra al cajero
 * para que la confirme y, si lo hace, se reenvía confirmada. Si no la confirma, no se agrega ni cambia nada.
 */
export async function conConfirmacion<T>(
  enviar: (confirmada: boolean) => Promise<T>,
  confirmar: (mensaje: string) => boolean = (mensaje) => window.confirm(mensaje),
): Promise<T> {
  try {
    return await enviar(false)
  } catch (error) {
    if (!(error instanceof ErrorDeApi) || error.codigo !== 'CANTIDAD_POR_CONFIRMAR') throw error
    if (!confirmar(error.message)) throw new Error(CANTIDAD_SIN_CONFIRMAR)
    return enviar(true)
  }
}
