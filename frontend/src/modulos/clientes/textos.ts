import type { BusquedaNit, Cliente, ClienteListado } from '../../api/clientes'

// Textos del cliente de la venta (EP-05), separados del componente para probarlos sin renderizar.

/** HU-05.01 crit. 3: el receptor de la factura cuando la venta no tiene cliente. */
export const CONSUMIDOR_FINAL = 'CF · Consumidor Final'

/** HU-05.11 crit. 3: la misma forma que valida el servidor (nombre@dominio.tld, sin espacios). */
export const correoValido = (correo: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(correo.trim())

export const CORREO_INVALIDO = 'El correo no es válido, por ejemplo cliente@correo.com.'

/** HU-05.11 crit. 4: lo que ve el cajero sobre el envío de la factura. */
export const avisoCorreoFactura = (correo: string | null) =>
  correo ? `La factura se enviará a ${correo}.` : 'Sin correo: la factura solo se imprime.'

/** HU-05.03 crit. 2: el aviso de un NIT mal escrito; no se usa. */
export const NIT_CLIENTE_INVALIDO = 'El NIT no es válido: revise los dígitos y el verificador (puede terminar en K).'

/** El NIT como se acostumbra leer: con guion antes del verificador. */
export const nitConGuion = (nit: string) => (nit.length > 1 ? `${nit.slice(0, -1)}-${nit.slice(-1)}` : nit)

export const describirCliente = (cliente: Cliente | null) => {
  if (!cliente) return CONSUMIDOR_FINAL
  const identificacion = cliente.tipoIdentificacion === 'CUI' ? `CUI ${cliente.nit}` : `NIT ${nitConGuion(cliente.nit)}`
  return `${identificacion} · ${cliente.nombre} · ${cliente.direccion}`
}

/** HU-05.06 crit. 2: el CUI tiene 13 dígitos; se aceptan espacios y guiones al escribirlo. */
export const normalizarCui = (cui: string) => cui.replace(/[\s-]/g, '')
export const cuiValido = (cui: string) => /^\d{13}$/.test(normalizarCui(cui))
export const CUI_INVALIDO = 'El CUI debe tener 13 dígitos numéricos.'

export const identificacionCliente = (cliente: ClienteListado) =>
  `${cliente.tipoIdentificacion} ${cliente.tipoIdentificacion === 'NIT'
    ? nitConGuion(cliente.identificacion)
    : cliente.identificacion}`

export const fechaUltimaCompra = (iso: string | null) => iso
  ? new Date(iso).toLocaleString('es-GT', {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: 'America/Guatemala',
    })
  : 'Sin compras'

export const resumenClientes = (pagina: number, tamano: number, total: number) => {
  if (total === 0) return '0 clientes'
  const paginas = Math.ceil(total / tamano)
  return `Página ${pagina + 1} de ${paginas} · ${total} ${total === 1 ? 'cliente' : 'clientes'}`
}

/** Qué se le dice al cajero después de buscar el NIT. */
export const avisoBusqueda = (busqueda: BusquedaNit) => {
  switch (busqueda.origen) {
    case 'REGISTRADO':
      return `Cliente cargado: ${busqueda.cliente?.nombre}.`
    case 'CONSULTADO':
      return `Nombre obtenido del certificador: ${busqueda.cliente?.nombre}. Se guardó para las próximas compras.`
    default:
      return (
        busqueda.mensaje ??
        `No se encontró el ${busqueda.tipoIdentificacion}. Escriba el nombre del cliente o facture como CF.`
      )
  }
}
