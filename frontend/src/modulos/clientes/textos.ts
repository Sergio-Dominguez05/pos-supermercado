import type { BusquedaNit, Cliente } from '../../api/clientes'

// Textos del cliente de la venta (EP-05), separados del componente para probarlos sin renderizar.

/** HU-05.01 crit. 3: el receptor de la factura cuando la venta no tiene cliente. */
export const CONSUMIDOR_FINAL = 'CF · Consumidor Final'

/** HU-05.03 crit. 2: el aviso de un NIT mal escrito; no se usa. */
export const NIT_CLIENTE_INVALIDO = 'El NIT no es válido: revise los dígitos y el verificador (puede terminar en K).'

/** El NIT como se acostumbra leer: con guion antes del verificador. */
export const nitConGuion = (nit: string) => (nit.length > 1 ? `${nit.slice(0, -1)}-${nit.slice(-1)}` : nit)

export const describirCliente = (cliente: Cliente | null) =>
  cliente ? `NIT ${nitConGuion(cliente.nit)} · ${cliente.nombre} · ${cliente.direccion}` : CONSUMIDOR_FINAL

/** Qué se le dice al cajero después de buscar el NIT. */
export const avisoBusqueda = (busqueda: BusquedaNit) => {
  switch (busqueda.origen) {
    case 'REGISTRADO':
      return `Cliente cargado: ${busqueda.cliente?.nombre}.`
    case 'CONSULTADO':
      return `Nombre obtenido del certificador: ${busqueda.cliente?.nombre}. Se guardó para las próximas compras.`
    default:
      return busqueda.mensaje ?? 'No se encontró el NIT. Escriba el nombre del cliente o facture como CF.'
  }
}
