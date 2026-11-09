import type { Producto } from '../../api/productos'

// Textos del catálogo (EP-03), separados de los componentes para probarlos sin renderizar.

export const formatoQuetzales = (monto: number) => `Q${monto.toFixed(2)}`

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
