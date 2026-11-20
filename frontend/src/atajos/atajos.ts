// Mapa central de atajos de teclado de la caja (HU-06.17, Architecture.md §7.6).
// Las pantallas registran su acción por nombre; la tecla solo se define aquí.

export type AccionCaja =
  | 'ayuda'
  | 'buscarProducto'
  | 'consultarPrecio'
  | 'cambiarCantidad'
  | 'eliminarLinea'
  | 'asignarCliente'
  | 'suspenderVenta'
  | 'retomarVenta'
  | 'reimprimirUltima'
  | 'cancelarVenta'
  | 'cobrar'

export interface Atajo {
  tecla: string
  accion: AccionCaja
  descripcion: string
}

export const ATAJOS: readonly Atajo[] = [
  { tecla: 'F1', accion: 'ayuda', descripcion: 'Mostrar atajos' },
  { tecla: 'F2', accion: 'buscarProducto', descripcion: 'Buscar producto por descripción' },
  { tecla: 'F3', accion: 'consultarPrecio', descripcion: 'Consultar precio' },
  { tecla: 'F4', accion: 'cambiarCantidad', descripcion: 'Cambiar cantidad' },
  { tecla: 'Delete', accion: 'eliminarLinea', descripcion: 'Eliminar línea' },
  { tecla: 'F6', accion: 'asignarCliente', descripcion: 'Asignar cliente (NIT/CUI/CF)' },
  { tecla: 'F7', accion: 'suspenderVenta', descripcion: 'Suspender venta' },
  { tecla: 'F8', accion: 'retomarVenta', descripcion: 'Retomar venta' },
  { tecla: 'F9', accion: 'reimprimirUltima', descripcion: 'Reimprimir última factura' },
  { tecla: 'F10', accion: 'cancelarVenta', descripcion: 'Cancelar venta' },
  { tecla: 'F12', accion: 'cobrar', descripcion: 'Cobrar' },
]

/**
 * Acciones del mapa cuya pantalla todavía no existe, con la historia que la trae. Su tecla ya está reservada: al
 * presionarla la caja avisa con qué historia llega, y la ayuda (F1) la muestra como pendiente. Cuando la historia
 * registre su manejador en la pantalla de venta, pasa de aquí a `ACCIONES_DE_LA_VENTA` (lo exige `teclado.test.ts`).
 */
export const ACCIONES_PENDIENTES: Partial<Record<AccionCaja, string>> = {
  consultarPrecio: 'HU-03.10 (#28)',
}

/** Nombre de la tecla para mostrarlo en pantalla: el teclado en español rotula "Supr" a la tecla Delete. */
export const rotuloDeTecla = (tecla: string) => (tecla === 'Delete' ? 'Supr' : tecla)

export function accionDeTecla(tecla: string): AccionCaja | undefined {
  return ATAJOS.find((atajo) => atajo.tecla === tecla)?.accion
}

/** El aviso al presionar el atajo de una acción que todavía no existe. */
export const avisoPendiente = (atajo: Atajo) =>
  `${atajo.descripcion} (${rotuloDeTecla(atajo.tecla)}) estará disponible con ${ACCIONES_PENDIENTES[atajo.accion]}.`
