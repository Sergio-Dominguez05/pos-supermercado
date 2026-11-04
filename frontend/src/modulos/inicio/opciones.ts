// Opciones del menú principal y el permiso que exige cada una (matriz de permisos de userStoriesE1.md).
// Ocultarlas es solo comodidad: el backend valida cada permiso (HU-01.11).

export interface OpcionMenu {
  etiqueta: string
  permiso: string
}

export const OPCIONES: OpcionMenu[] = [
  { etiqueta: 'Venta', permiso: 'VENTA_REGISTRAR' },
  { etiqueta: 'Caja', permiso: 'CAJA_OPERAR_PROPIA' },
  { etiqueta: 'Clientes', permiso: 'CLIENTE_GESTIONAR' },
  { etiqueta: 'Facturas', permiso: 'FACTURA_REIMPRIMIR' },
  { etiqueta: 'Catálogo', permiso: 'PRODUCTO_CONSULTAR' },
  { etiqueta: 'Reportes', permiso: 'REPORTE_CONSULTAR' },
  { etiqueta: 'Bitácora', permiso: 'BITACORA_CONSULTAR' },
  { etiqueta: 'Usuarios', permiso: 'USUARIO_ADMINISTRAR' },
  { etiqueta: 'Configuración', permiso: 'CONFIGURACION_MODIFICAR' },
]

export const opcionesPara = (permisos: readonly string[]) =>
  OPCIONES.filter((opcion) => permisos.includes(opcion.permiso))
