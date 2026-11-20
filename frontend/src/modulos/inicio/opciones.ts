// Opciones del menú principal y el permiso que exige cada una (matriz de permisos de userStoriesE1.md).
// Ocultarlas es solo comodidad: el backend valida cada permiso (HU-01.11).

export interface OpcionMenu {
  etiqueta: string
  permiso: string
  /** Pantalla disponible; sin ruta, la opción se muestra como "Próximamente". */
  ruta?: string
}

export const OPCIONES: OpcionMenu[] = [
  // HU-06.01: la pantalla de venta (también es el destino del Cajero al iniciar sesión).
  { etiqueta: 'Venta', permiso: 'VENTA_REGISTRAR', ruta: '/venta' },
  // HU-09.08 a 09.10: cerrar la caja propia y reimprimir los cierres.
  { etiqueta: 'Caja', permiso: 'CAJA_OPERAR_PROPIA', ruta: '/caja' },
  // HU-09.12: cerrar el turno que otro usuario dejó abierto (Supervisor y Administrador).
  { etiqueta: 'Cerrar caja de otro usuario', permiso: 'CAJA_CERRAR_OTRA', ruta: '/caja/turnos' },
  { etiqueta: 'Clientes', permiso: 'CLIENTE_GESTIONAR', ruta: '/clientes' },
  // HU-08.12 crit. 2: buscar y reimprimir facturas (el cajero, las de su turno).
  { etiqueta: 'Facturas', permiso: 'FACTURA_REIMPRIMIR', ruta: '/facturas' },
  // HU-08.06 crit. 3: las ventas pendientes de facturar, para quien gestiona facturas (Supervisor y Administrador).
  { etiqueta: 'Facturas rechazadas', permiso: 'FACTURA_ANULAR', ruta: '/facturas/rechazadas' },
  // HU-08.13 y 08.14: el historial de facturas con su detalle (Supervisor y Administrador).
  { etiqueta: 'Historial de facturas', permiso: 'FACTURA_ANULAR', ruta: '/facturas/historial' },
  // HU-08.10: buscar y anular facturas (Supervisor y Administrador).
  { etiqueta: 'Anular facturas', permiso: 'FACTURA_ANULAR', ruta: '/facturas/anular' },
  // HU-08.08 y 08.09: los comprobantes en contingencia.
  { etiqueta: 'Contingencia', permiso: 'FACTURA_ANULAR', ruta: '/facturas/contingencia' },
  // HU-03.04: la lista del catálogo; desde ahí, quien tiene permiso crea y edita productos.
  { etiqueta: 'Catálogo', permiso: 'PRODUCTO_CONSULTAR', ruta: '/catalogo' },
  // HU-10.01, 10.02 y 10.15: los reportes, solo para quien tiene el permiso (Administrador y Supervisor).
  { etiqueta: 'Reportes', permiso: 'REPORTE_CONSULTAR', ruta: '/reportes' },
  { etiqueta: 'Bitácora', permiso: 'BITACORA_CONSULTAR', ruta: '/bitacora' },
  { etiqueta: 'Usuarios', permiso: 'USUARIO_ADMINISTRAR', ruta: '/usuarios' },
  // HU-01.10: los permisos del Supervisor y del Cajero (solo el Administrador).
  { etiqueta: 'Permisos por rol', permiso: 'PERMISO_CONFIGURAR', ruta: '/permisos' },
  // La configuración abre en las cajas (HU-12.05); desde ahí se llega al emisor (HU-12.01), al establecimiento
  // (HU-12.02) y a la conexión FEL (HU-12.03). El resto de EP-12 se agrega a NavConfiguracion.
  { etiqueta: 'Configuración', permiso: 'CONFIGURACION_MODIFICAR', ruta: '/configuracion/cajas' },
]

export const opcionesPara = (permisos: readonly string[]) =>
  OPCIONES.filter((opcion) => permisos.includes(opcion.permiso))
