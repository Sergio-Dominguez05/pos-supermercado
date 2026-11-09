import type { Producto } from '../../api/productos'

// Textos del catálogo (EP-03), separados de los componentes para probarlos sin renderizar.

export const formatoQuetzales = (monto: number) => `Q${monto.toFixed(2)}`

export const avisoProductoEditado = (producto: Producto) =>
  `Se actualizó ${producto.descripcion} (${producto.codigoBarras}): ahora cuesta ${formatoQuetzales(producto.precio)}. ` +
  'El precio nuevo se aplica a lo que se agregue a una venta desde ahora.'

export const avisoProductoCreado = (producto: Producto) =>
  `Se registró ${producto.descripcion} (${producto.codigoBarras}) a ${formatoQuetzales(producto.precio)}. ` +
  'Ya está disponible para la venta.'
