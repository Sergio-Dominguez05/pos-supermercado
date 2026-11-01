// Crea .env local solo si falta; nunca sobrescribe credenciales existentes.
import { copyFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
const ejemplo = resolve(import.meta.dirname, '..', '.env.example')
const destino = resolve(import.meta.dirname, '..', '.env')
if (!existsSync(destino)) {
  copyFileSync(ejemplo, destino)
  console.log('Se creó .env con valores locales de ejemplo (solo desarrollo).')
} else {
  console.log('El .env existente se conservó sin cambios.')
}
