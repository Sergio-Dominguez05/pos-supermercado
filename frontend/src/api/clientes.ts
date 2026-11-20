import { pedir } from './cliente'

/** EP-05: un cliente registrado. El NIT viene normalizado: sin guion ni espacios (HU-05.03 crit. 3). */
export interface Cliente {
  id: number
  nit: string
  nombre: string
  /** "Ciudad" si no se indicó otra (HU-05.05 crit. 1). */
  direccion: string
  correo: string | null
}

/**
 * Cómo terminó la búsqueda por NIT: ya estaba registrado (HU-05.02), se registró con el nombre del certificador
 * (HU-05.04 crit. 2), o no se encontró / no hubo respuesta (HU-05.04 crit. 3), con el mensaje para el cajero.
 */
export interface BusquedaNit {
  origen: 'REGISTRADO' | 'CONSULTADO' | 'NO_EXISTE' | 'SIN_CONSULTA'
  nit: string
  cliente: Cliente | null
  mensaje: string | null
}

/** El cliente de la venta en curso; sin cliente es a Consumidor Final (HU-05.01). */
export interface ClienteDeVenta {
  consumidorFinal: boolean
  cliente: Cliente | null
}

export interface DatosCliente {
  nit: string
  nombre: string
  direccion: string
  correo: string
}

/** HU-05.10: cliente mostrado en el directorio. */
export interface ClienteListado {
  id: number
  tipoIdentificacion: 'NIT' | 'CUI'
  identificacion: string
  nombre: string
  direccion: string
  correo: string | null
  ultimaCompra: string | null
}

export interface FiltroClientes {
  q: string
  pagina: number
}

export const FILTRO_CLIENTES_INICIAL: FiltroClientes = { q: '', pagina: 0 }

export interface PaginaClientes {
  clientes: ClienteListado[]
  pagina: number
  tamano: number
  total: number
}

export const claveListaClientes = (filtro: FiltroClientes) => ['clientes', 'lista', filtro] as const

export function listarClientes(filtro: FiltroClientes) {
  const parametros = new URLSearchParams()
  if (filtro.q.trim()) parametros.set('q', filtro.q.trim())
  if (filtro.pagina > 0) parametros.set('pagina', String(filtro.pagina))
  return pedir<PaginaClientes>(`/api/clientes?${parametros}`)
}

export const claveClienteDeVenta = (ventaId: number) => ['ventas', ventaId, 'cliente'] as const

export const buscarClientePorNit = (nit: string) =>
  pedir<BusquedaNit>(`/api/clientes/nit/${encodeURIComponent(nit.trim())}`)

export const registrarCliente = (datos: DatosCliente) =>
  pedir<Cliente>('/api/clientes', { method: 'POST', body: JSON.stringify(datos) })

export const obtenerClienteDeVenta = () => pedir<ClienteDeVenta>('/api/ventas/actual/cliente')

/** Asigna el cliente a la venta en curso; `null` vuelve a Consumidor Final (HU-05.01 crit. 2). */
export const asignarClienteAVenta = (clienteId: number | null) =>
  pedir<ClienteDeVenta>('/api/ventas/actual/cliente', { method: 'PUT', body: JSON.stringify({ clienteId }) })
