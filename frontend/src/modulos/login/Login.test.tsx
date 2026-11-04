import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { Login } from './Login'

const html = renderToString(
  <QueryClientProvider client={new QueryClient()}>
    <MemoryRouter>
      <Login />
    </MemoryRouter>
  </QueryClientProvider>,
)

/** Etiqueta `<input>` completa del campo, sin depender del orden de los atributos. */
const campo = (nombre: string) => {
  const etiqueta = html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))
  if (!etiqueta) throw new Error(`No existe el campo ${nombre}`)
  return etiqueta[0]
}

describe('pantalla de inicio de sesión', () => {
  it('la contraseña no se muestra mientras se escribe (HU-01.01 crit. 4)', () => {
    expect(campo('contrasena')).toContain('type="password"')
    expect(campo('contrasena')).toMatch(/autocomplete="current-password"/i)
  })

  it('pide usuario y contraseña', () => {
    expect(campo('username')).toContain('required')
    expect(campo('username')).not.toContain('type="password"')
    expect(campo('contrasena')).toContain('required')
  })
})
