import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { RutaProtegida } from './RutaProtegida'
import { CLAVE_SESION } from './sesion'

const CAJERA: UsuarioSesion = {
  username: 'lucia.mendez', nombreCompleto: 'Lucía Méndez', rol: 'CAJERO', rolNombre: 'Cajero',
  credencialTemporal: false, permisos: ['VENTA_REGISTRAR'],
}

const renderizar = (sesion: UsuarioSesion | null) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>
        <RutaProtegida>
          <p>Contenido protegido</p>
        </RutaProtegida>
      </MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

describe('pantallas protegidas', () => {
  it('con sesión muestran quién la tiene y el botón para cerrarla (HU-01.16 crit. 1)', () => {
    const html = renderizar(CAJERA)

    expect(html).toContain('Lucía Méndez · Cajero')
    expect(html).toMatch(/<button[^>]*>Cerrar sesión<\/button>/)
    expect(html).toContain('Contenido protegido')
  })

  it('la barra muestra la caja cuando el equipo está vinculado (HU-12.05 crit. 4)', () => {
    expect(renderizar({ ...CAJERA, cajaNombre: 'Caja 2' })).toContain('Lucía Méndez · Cajero · Caja 2')
    expect(renderizar({ ...CAJERA, cajaNombre: null })).not.toContain('Cajero · ')
  })

  it('sin sesión no muestran ni el contenido ni la barra', () => {
    const html = renderizar(null)

    expect(html).not.toContain('Contenido protegido')
    expect(html).not.toContain('Cerrar sesión')
  })
})
