import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CambioCredencial } from './CambioCredencial'
import { RutaProtegida, RUTA_CAMBIO_CREDENCIAL } from './RutaProtegida'
import { avisoCredencialTemporal, CLAVE_SESION } from './sesion'

const SUPERVISORA: UsuarioSesion = {
  username: 'ana.sup',
  nombreCompleto: 'Ana Supervisora',
  rol: 'SUPERVISOR',
  rolNombre: 'Supervisor',
  credencialTemporal: true,
  permisos: ['REPORTE_CONSULTAR'],
}

const renderizar = (contenido: ReactNode, sesion: UsuarioSesion, ruta = '/inicio') => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[ruta]}>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const protegido = (
  <RutaProtegida>
    <p>Contenido protegido</p>
  </RutaProtegida>
)

describe('cambio obligatorio de la credencial temporal (HU-01.15)', () => {
  it('con una credencial temporal ninguna pantalla se muestra: lleva al cambio (crit. 1 y 2)', () => {
    expect(renderizar(protegido, SUPERVISORA, '/inicio')).not.toContain('Contenido protegido')
    expect(renderizar(protegido, SUPERVISORA, '/reportes')).not.toContain('Contenido protegido')
    expect(RUTA_CAMBIO_CREDENCIAL).toBe('/credencial')
  })

  it('la pantalla de cambio sí se muestra, con la barra para poder salir', () => {
    const html = renderizar(protegido, SUPERVISORA, RUTA_CAMBIO_CREDENCIAL)

    expect(html).toContain('Contenido protegido')
    expect(html).toMatch(/<button[^>]*>Cerrar sesión<\/button>/)
  })

  it('sin credencial temporal todo sigue igual', () => {
    expect(renderizar(protegido, { ...SUPERVISORA, credencialTemporal: false }, '/inicio')).toContain(
      'Contenido protegido',
    )
  })

  it('explica por qué debe cambiarla y pide la credencial temporal, sin enlace a Inicio', () => {
    const contrasena = renderizar(<CambioCredencial />, SUPERVISORA, RUTA_CAMBIO_CREDENCIAL)
    expect(contrasena).toContain(avisoCredencialTemporal(false))
    expect(contrasena).toContain('Contraseña temporal')
    expect(contrasena).not.toContain('>Inicio</a>')

    const pin = renderizar(
      <CambioCredencial />,
      { ...SUPERVISORA, rol: 'CAJERO', rolNombre: 'Cajero' },
      RUTA_CAMBIO_CREDENCIAL,
    )
    expect(pin).toContain('Su PIN es temporal. Para continuar, cámbielo por uno que solo usted conozca.')
    expect(pin).toContain('PIN temporal')

    const normal = renderizar(<CambioCredencial />, { ...SUPERVISORA, credencialTemporal: false }, '/credencial')
    expect(normal).not.toContain('es temporal')
    expect(normal).toContain('Contraseña actual')
    expect(normal).toContain('>Inicio</a>')
  })
})
