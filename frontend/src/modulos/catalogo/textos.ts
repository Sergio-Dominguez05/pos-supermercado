import type { Producto } from '../../api/productos'

// Textos del catálogo (EP-03), separados de los componentes para probarlos sin renderizar.

export const formatoQuetzales = (monto: number) => `Q${monto.toFixed(2)}`

/** HU-03.09 crit. 2: el mismo mensaje que da el servidor al intentar venderlo. */
export const avisoProductoInactivo = (producto: Producto) =>
  `${producto.descripcion} (${producto.codigoBarras}) no está disponible para la venta.`

/** HU-03.08 crit. 1: el código se muestra como lo normaliza el servidor (sin espacios, en mayúsculas). */
export const avisoCodigoNoEncontrado = (codigo: string) =>
  `El código ${codigo.replace(/\s+/g, '').toUpperCase()} no existe en el catálogo.`

const FORMATO_FECHA = new Intl.DateTimeFormat('es-GT', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Guatemala',
})

/** Fecha de la última modificación, en hora de Guatemala (HU-03.04 crit. 1). */
export const formatoFecha = (iso: string) => FORMATO_FECHA.format(new Date(iso))

/** "Página 2 de 5 · 112 productos"; sin resultados, solo la cantidad (HU-03.04 crit. 5). */
export const resumenPagina = (pagina: number, tamano: number, total: number) => {
  const paginas = Math.max(1, Math.ceil(total / tamano))
  const cantidad = `${total} ${total === 1 ? 'producto' : 'productos'}`
  return total === 0 ? cantidad : `Página ${pagina + 1} de ${paginas} · ${cantidad}`
}

export const avisoProductoEditado = (producto: Producto) =>
  `Se actualizó ${producto.descripcion} (${producto.codigoBarras}): ahora cuesta ${formatoQuetzales(producto.precio)}. ` +
  'El precio nuevo se aplica a lo que se agregue a una venta desde ahora.'

export const avisoEstadoProducto = (producto: Producto) =>
  producto.activo
    ? `Se activó ${producto.descripcion}: ya se puede vender.`
    : `Se desactivó ${producto.descripcion}: ya no se puede agregar a nuevas ventas.`

export const confirmacionDesactivarProducto = (producto: Producto) =>
  `¿Desactivar ${producto.descripcion} (${producto.codigoBarras})? No se podrá agregar a nuevas ventas hasta que lo ` +
  'vuelva a activar. El producto no se borra: sus ventas y facturas se conservan.'

export const avisoProductoCreado = (producto: Producto) =>
  `Se registró ${producto.descripcion} (${producto.codigoBarras}) a ${formatoQuetzales(producto.precio)}. ` +
  'Ya está disponible para la venta.'
