// Operaciones Docker Compose desde la raíz del monorepo.
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { entorno, raiz } from './entorno.mjs'
const opcion = process.argv[2] ?? 'up'
const comandos = {
  up: ['up', '-d', '--wait'],
  down: ['down'],
  logs: ['logs', '-f', 'postgres'],
}
if (!comandos[opcion]) { console.error('Uso: node scripts/db.mjs up|down|logs'); process.exit(2) }
if (!existsSync(resolve(raiz, '.env'))) {
  console.error('Falta .env. Ejecuta primero npm run setup.'); process.exit(1)
}
const args = ['compose', '--project-directory', raiz, ...comandos[opcion]]
const windows = process.platform === 'win32'
const r = spawnSync(windows ? 'cmd.exe' : 'docker', windows ? ['/d', '/c', 'docker', ...args] : args, {
  cwd: raiz, env: entorno(), stdio: 'inherit',
})
if (r.error) console.error('No se pudo ejecutar Docker:', r.error.message)
process.exit(r.status ?? 1)
