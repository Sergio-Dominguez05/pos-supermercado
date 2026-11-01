// Arranque coordinado de PostgreSQL, backend Java y frontend React.
// No necesita concurrently ni otras dependencias npm en la raíz.
import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { entorno, raiz } from './entorno.mjs'
const windows = process.platform === 'win32'
if (!existsSync(resolve(raiz, '.env'))) {
  console.error('Falta .env. Ejecuta npm run setup o npm install.'); process.exit(1)
}
if (!existsSync(resolve(raiz, 'frontend/node_modules'))) {
  console.error('Faltan dependencias del frontend. Ejecuta npm install en la raíz.'); process.exit(1)
}
console.log('[POS] Iniciando PostgreSQL con Docker Compose...')
const composeArgs = ['compose', '--project-directory', raiz, 'up', '-d', '--wait']
const db = spawnSync(windows ? 'cmd.exe' : 'docker', windows ? ['/d', '/c', 'docker', ...composeArgs] : composeArgs, { cwd: raiz, env: entorno(), stdio: 'inherit' })
if (db.status !== 0) {
  console.error('[POS] Docker no pudo iniciar PostgreSQL. Abre Docker Desktop y comprueba el puerto 5433.')
  if (db.error) console.error(db.error.message)
  process.exit(db.status ?? 1)
}
const npm = windows ? 'cmd.exe' : 'npm'
function ejecutar(nombre, args) {
  const parametros = windows ? ['/d', '/c', 'npm', ...args] : args
  console.log(`[POS] Iniciando ${nombre}...`)
  return spawn(npm, parametros, { cwd: raiz, env: entorno(), stdio: 'inherit' })
}
const backend = ejecutar('Spring Boot', ['run', 'dev:backend'])
const frontend = ejecutar('Vite', ['run', 'dev:frontend'])
let cerrando = false
function finalizar(codigo) {
  if (cerrando) return
  cerrando = true
  console.log('\n[POS] Finalizando procesos de desarrollo...')
  for (const hijo of [backend, frontend]) {
    if (!hijo.pid) continue
    if (windows) spawnSync('taskkill', ['/pid', String(hijo.pid), '/T', '/F'], { stdio: 'ignore' })
    else hijo.kill('SIGTERM')
  }
  process.exitCode = codigo
}
for (const [nombre, proceso] of [['Spring Boot', backend], ['Vite', frontend]]) {
  proceso.on('error', (e) => { console.error(`${nombre}: ${e.message}`); finalizar(1) })
  proceso.on('exit', (codigo) => { if (!cerrando) { console.error(`${nombre} finalizó (${codigo}).`); finalizar(codigo === 0 ? 0 : 1) } })
}
process.on('SIGINT', () => finalizar(0))
process.on('SIGTERM', () => finalizar(0))
