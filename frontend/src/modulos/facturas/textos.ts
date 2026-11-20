import type { FacturaRechazada } from '../../api/facturas'

// Textos de las facturas rechazadas (HU-08.06), separados de los componentes para probarlos sin renderizar.

/** Crit. 3: lo que hace el cajero cuando el rechazo no se corrige en caja. */
export const AVISO_SUPERVISOR =
  'La venta quedó pendiente de facturar en "Facturas rechazadas". Avise a su supervisor para que la revise y la reenvíe.'

export const TIPOS_DE_RECHAZO: Record<FacturaRechazada['tipo'], string> = {
  RECEPTOR: 'Cliente',
  MONTOS: 'Montos',
  EMISOR: 'Emisor',
  OTRO: 'Otro',
}

/** El contador del menú principal: cuántas ventas esperan su factura. */
export const rotuloRechazadas = (cantidad: number) =>
  cantidad === 0 ? 'Facturas rechazadas' : `Facturas rechazadas (${cantidad})`

/** HU-08.10: la anulación se confirma antes de enviarla al certificador. */
export const confirmacionAnular = (serie: string, numero: string, ventaNumero: number) =>
  `¿Anular la factura ${serie}-${numero} de la venta N.º ${ventaNumero}? La anulación se envía a la SAT y no se ` +
  'puede deshacer.'

export const avisoAnulada = (serie: string, numero: string) => `La factura ${serie}-${numero} quedó anulada.`

// Contingencia (HU-08.07 a HU-08.09).

/** HU-08.07 crit. 3: el aviso permanente de la pantalla de venta. */
export const avisoContingenciaVenta = (pendientes: number) =>
  'Operando en contingencia: ' +
  (pendientes === 1 ? 'hay 1 comprobante pendiente' : `hay ${pendientes} comprobantes pendientes`) +
  ' de certificar. Las ventas siguen; se certificarán cuando vuelva la conexión.'

/** HU-08.09 crit. 1 y 2: el aviso del supervisor, con el más antiguo y su plazo. */
export const avisoContingenciaSupervisor = (pendientes: number, dias: number, vence: string | null) =>
  `${pendientes === 1 ? 'Hay 1 comprobante' : `Hay ${pendientes} comprobantes`} en contingencia. El más antiguo ` +
  `tiene ${dias === 1 ? '1 día' : `${dias} días`}` + (vence ? `; vence el ${fechaCorta(vence)}.` : '.')

const fechaCorta = (dia: string) => {
  const [anio, mes, d] = dia.split('-')
  return `${d}/${mes}/${anio}`
}
