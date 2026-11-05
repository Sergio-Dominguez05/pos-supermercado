import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CambioCredencial, FormularioCredencial } from './CambioCredencial'
import { RutaProtegida } from './RutaProtegida'
import { CLAVE_SESION } from './sesion'

const renderizar = (contenido: ReactNode, sesion?: Partial<UsuarioSesion>) => {
  const cliente = new QueryClient()
  if (sesion) cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

/** Etiqueta `<input>` completa del campo, sin depender del orden de los atributos. */
const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('cambio de credencial propia (HU-01.13)', () => {
  it('pide la actual, la nueva y su confirmación, todas ocultas (crit. 1)', () => {
    const html = renderizar(<FormularioCredencial esPin={false} />)

    for (const nombre of ['actual', 'nueva', 'confirmacion']) {
      expect(campo(html, nombre)).toContain('type="password"')
      expect(campo(html, nombre)).toContain('required')
    }
  })

  it('la nueva contraseña pide al menos 8 caracteres (crit. 2)', () => {
    const html = renderizar(<FormularioCredencial esPin={false} />)

    expect(campo(html, 'nueva')).toMatch(/minlength="8"/i)
    expect(campo(html, 'nueva')).toMatch(/autocomplete="new-password"/i)
    expect(campo(html, 'actual')).toMatch(/autocomplete="current-password"/i)
  })

  it('para el Cajero son PIN numéricos de 4 a 6 dígitos (crit. 2)', () => {
    const html = renderizar(<FormularioCredencial esPin />)

    for (const nombre of ['actual', 'nueva', 'confirmacion']) {
      expect(campo(html, nombre)).toMatch(/inputmode="numeric"/i)
      expect(campo(html, nombre)).toMatch(/maxlength="6"/i)
    }
    expect(html).toContain('Cambiar PIN')
  })

  it('el título se adapta al rol de la sesión', () => {
    expect(renderizar(<CambioCredencial />, { rol: 'CAJERO' })).toContain('<h1>Cambiar PIN</h1>')
    expect(renderizar(<CambioCredencial />, { rol: 'SUPERVISOR' })).toContain('<h1>Cambiar contraseña</h1>')
  })

  it('la barra superior lleva al cambio de credencial', () => {
    const sesion: UsuarioSesion = {
      username: 'ana', nombreCompleto: 'Ana', rol: 'CAJERO', rolNombre: 'Cajero', credencialTemporal: false,
      permisos: [],
    }
    const html = renderizar(<RutaProtegida><p /></RutaProtegida>, sesion)

    expect(html).toMatch(/<a[^>]*href="\/credencial"[^>]*>Cambiar PIN<\/a>/)
  })
})
