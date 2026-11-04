import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { FormularioLogin, Login } from './Login'
import { destinoInicial } from './sesion'

const renderizar = (contenido: ReactNode) =>
  renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  )

/** Etiqueta `<input>` completa del campo, sin depender del orden de los atributos. */
const campo = (html: string, nombre: string) => {
  const etiqueta = html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))
  if (!etiqueta) throw new Error(`No existe el campo ${nombre}`)
  return etiqueta[0]
}

describe('pantalla de inicio de sesión', () => {
  const html = renderizar(<Login />)

  it('ofrece entrar con contraseña o con PIN, empezando por contraseña', () => {
    expect(html).toMatch(/role="tab"[^>]*aria-selected="true"[^>]*>Contraseña</)
    expect(html).toMatch(/role="tab"[^>]*aria-selected="false"[^>]*>PIN</)
  })

  it('la contraseña no se muestra mientras se escribe (HU-01.01 crit. 4)', () => {
    expect(campo(html, 'contrasena')).toContain('type="password"')
    expect(campo(html, 'contrasena')).toMatch(/autocomplete="current-password"/i)
  })

  it('pide usuario y contraseña', () => {
    expect(campo(html, 'username')).toContain('required')
    expect(campo(html, 'username')).not.toContain('type="password"')
    expect(campo(html, 'contrasena')).toContain('required')
  })
})

describe('inicio de sesión con PIN (HU-01.02)', () => {
  const pin = campo(renderizar(<FormularioLogin modo="pin" />), 'pin')

  it('el PIN no se muestra y abre el teclado numérico (crit. 3)', () => {
    expect(pin).toContain('type="password"')
    expect(pin).toMatch(/inputmode="numeric"/i)
  })

  it('acepta solo de 4 a 6 dígitos (crit. 2)', () => {
    expect(pin).toContain('pattern="\\d{4,6}"')
    expect(pin).toMatch(/minlength="4"/i)
    expect(pin).toMatch(/maxlength="6"/i)
    expect(pin).toContain('required')
  })

  it('el cajero entra a la pantalla de venta y los demás roles al inicio (crit. 1)', () => {
    expect(destinoInicial('CAJERO')).toBe('/venta')
    expect(destinoInicial('ADMIN')).toBe('/inicio')
    expect(destinoInicial('SUPERVISOR')).toBe('/inicio')
  })
})
