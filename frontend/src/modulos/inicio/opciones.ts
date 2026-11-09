// Opciones del menú principal y el permiso que exige cada una (matriz de permisos de userStoriesE1.md).
// Ocultarlas es solo comodidad: el backend valida cada permiso (HU-01.11).

export interface OpcionMenu {
  etiqueta: string
  permiso: string
  /** Pantalla disponible; sin ruta, la opción se muestra como "Próximamente". */
  ruta?: string
}

export const OPCIONES: OpcionMenu[] = [
  { etiqueta: 'Venta', permiso: 'VENTA_REGISTRAR' },
  { etiqueta: 'Caja', permiso: 'CAJA_OPERAR_PROPIA' },
  { etiqueta: 'Clientes', permiso: 'CLIENTE_GESTIONAR' },
  { etiqueta: 'Facturas', permiso: 'FACTURA_REIMPRIMIR' },
  { etiqueta: 'Catálogo', permiso: 'PRODUCTO_CONSULTAR' },
  // HU-03.01. Cuando exista la lista del catálogo (HU-03.04), el alta se ofrecerá desde allí.
  { etiqueta: 'Nuevo producto', permiso: 'PRODUCTO_EDITAR', ruta: '/catalogo/nuevo' },
  { etiqueta: 'Reportes', permiso: 'REPORTE_CONSULTAR' },
  { etiqueta: 'Bitácora', permiso: 'BITACORA_CONSULTAR', ruta: '/bitacora' },
  { etiqueta: 'Usuarios', permiso: 'USUARIO_ADMINISTRAR', ruta: '/usuarios' },
  // Por ahora la configuración tiene solo las cajas (HU-12.05); el resto de EP-12 se agrega aquí.
  { etiqueta: 'Configuración', permiso: 'CONFIGURACION_MODIFICAR', ruta: '/configuracion/cajas' },
]

export const opcionesPara = (permisos: readonly string[]) =>
  OPCIONES.filter((opcion) => permisos.includes(opcion.permiso))
