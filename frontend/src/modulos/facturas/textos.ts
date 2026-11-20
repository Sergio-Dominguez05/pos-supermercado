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
