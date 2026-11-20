import { pedir } from './cliente'
import type { FacturaDeVenta } from './pagos'

/** HU-08.06 crit. 3 y 4: una venta cobrada cuya factura rechazó el certificador, pendiente de facturar. */
export interface FacturaRechazada {
  ventaId: number
  ventaNumero: number
  cajaNombre: string
  cajero: string
  emitidaEn: string
  total: number
  receptorId: string
  receptorNombre: string
  tipo: 'RECEPTOR' | 'MONTOS' | 'EMISOR' | 'OTRO'
  /** Crit. 1: el motivo en lenguaje comprensible. */
  motivo: string
  corregibleEnCaja: boolean
  codigo: string | null
  mensajeCertificador: string | null
  /** Crit. 4: la respuesta completa del certificador. */
  respuesta: string | null
  rechazadaEn: string
}

export const CLAVE_FACTURAS_RECHAZADAS = ['facturas', 'rechazadas'] as const

export const listarFacturasRechazadas = () => pedir<FacturaRechazada[]>('/api/facturas/rechazadas')

/** El supervisor reenvía la factura con el mismo receptor, cuando ya se corrigió la causa (crit. 3). */
export const reenviarFacturaRechazada = (ventaId: number) =>
  pedir<FacturaDeVenta>(`/api/facturas/rechazadas/${ventaId}/reenviar`, { method: 'POST' })

/**
 * Crit. 2: el cajero corrige el cliente de su venta ya cobrada y reenvía la factura, sin volver a cobrar. `null`
 * la reenvía a Consumidor Final.
 */
export const reenviarFacturaDesdeCaja = (ventaId: number, clienteId: number | null) =>
  pedir<FacturaDeVenta>(`/api/ventas/${ventaId}/factura/reenviar`, {
    method: 'POST',
    body: JSON.stringify({ clienteId }),
  })
