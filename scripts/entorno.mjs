// Lee el .env raíz sin dependencias y sin ejecutar contenido como comandos.
// Variables definidas explícitamente en la terminal tienen prioridad.
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
export const raiz = resolve(import.meta.dirname, '..')
export function entorno() {
  const ruta = resolve(raiz, '.env')
  const variables = { ...process.env }
  if (!existsSync(ruta)) return variables
  for (const linea of readFileSync(ruta, 'utf8').split(/\r?\n/)) {
    const entrada = linea.trim()
    if (!entrada || entrada.startsWith('#')) continue
    const indice = entrada.indexOf('=')
    if (indice < 1) continue
    const clave = entrada.slice(0, indice).trim()
    if (!/^[A-Za-z_][A-Za-z_0-9]*$/.test(clave)) continue
    let valor = entrada.slice(indice + 1).trim()
    if ((valor.startsWith('"') && valor.endsWith('"')) || (valor.startsWith("'") && valor.endsWith("'"))) {
      valor = valor.slice(1, -1)
    }
    if (process.env[clave] === undefined) variables[clave] = valor
  }
  return variables
}
