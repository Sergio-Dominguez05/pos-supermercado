// HU-05.03: el NIT guatemalteco, igual que `compartido.dominio.Nit` del backend. Lo usan el emisor (HU-12.01) y, con
// EP-05, los clientes. El servidor vuelve a validarlo: esto solo avisa antes de enviar.

/** Crit. 3: sin guiones ni espacios y en mayúsculas. */
export const normalizarNit = (nit: string) => nit.replace(/[\s-]/g, '').toUpperCase()

/**
 * Crit. 1: módulo 11 de la SAT. Cada dígito del cuerpo se multiplica por su posición desde la derecha más uno; el
 * verificador es `(11 - suma mod 11) mod 11`, y 10 se escribe "K".
 */
export const verificadorNit = (cuerpo: string) => {
  const suma = [...cuerpo].reduce((total, digito, i) => total + Number(digito) * (cuerpo.length + 1 - i), 0)
  const resultado = (11 - (suma % 11)) % 11
  return resultado === 10 ? 'K' : String(resultado)
}

export const nitValido = (nit: string) => {
  const normal = normalizarNit(nit)
  return /^\d{1,12}[0-9K]$/.test(normal) && normal.at(-1) === verificadorNit(normal.slice(0, -1))
}
