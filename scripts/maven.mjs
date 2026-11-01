// Ejecuta Maven Wrapper con el sistema operativo adecuado; Spring Boot no usa npm.
import { spawn } from 'node:child_process'
import { resolve } from 'node:path'
import { entorno, raiz } from './entorno.mjs'
const windows = process.platform === 'win32'
const cwd = resolve(raiz, 'backend')
const args = process.argv.slice(2)
if (args.length === 0) {
  console.error('Indica la tarea Maven. Ejemplo: npm run dev:backend')
  process.exit(2)
}
const comando = windows ? 'cmd.exe' : './mvnw'
const params = windows ? ['/d', '/c', 'mvnw.cmd', ...args] : args
const hijo = spawn(comando, params, { cwd, env: entorno(), stdio: 'inherit' })
hijo.on('error', (error) => { console.error('No se pudo ejecutar Maven Wrapper:', error.message); process.exitCode = 1 })
hijo.on('exit', (code, signal) => { if (signal) process.exitCode = 1; else process.exitCode = code ?? 1 })
process.on('SIGTERM', () => hijo.kill('SIGTERM'))
