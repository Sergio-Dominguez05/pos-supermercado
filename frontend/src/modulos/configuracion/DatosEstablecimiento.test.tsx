import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_ESTABLECIMIENTO, guardarEstablecimiento, type Establecimiento } from '../../api/establecimiento'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioEstablecimiento, PaginaEstablecimiento } from './DatosEstablecimiento'
import { NavConfiguracion } from './NavConfiguracion'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }
const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const SIN_CONFIGURAR: Establecimiento = { configurado: false, codigoSat: null, nombre: null, direccion: null,
  codigoPostal: null, municipio: null, departamento: null, modificadoEn: null }
const GUARDADO: Establecimiento = { configurado: true, codigoSat: '1', nombre: 'Súper del Valle, zona 1',
  direccion: '6a. avenida 5-10, zona 1', codigoPostal: '01001', municipio: 'Guatemala', departamento: 'Guatemala',
  modificadoEn: '2026-11-17T03:00:00Z' }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_ESTABLECIMIENTO, GUARDADO)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('establecimiento SAT (HU-12.02)', () => {
  it('pide código, nombre y dirección, los tres obligatorios (crit. 1)', () => {
    const html = renderizar(<FormularioEstablecimiento guardado={SIN_CONFIGURAR} />)

    for (const nombre of ['codigoSat', 'nombre', 'direccion', 'codigoPostal', 'municipio', 'departamento'])
      expect(campo(html, nombre), nombre).toContain('required')
    expect(campo(html, 'codigoPostal')).toContain('pattern="[0-9]{5}"')
    expect(html).toContain('<option value="Quetzaltenango">')
    expect(campo(html, 'codigoSat')).toContain('pattern="[1-9][0-9]{0,5}"')
  })

  it('carga los datos guardados y explica que van en cada factura (crit. 2 y 3)', () => {
    const html = renderizar(<FormularioEstablecimiento guardado={GUARDADO} />)

    expect(campo(html, 'codigoSat')).toContain('value="1"')
    expect(campo(html, 'nombre')).toContain('value="Súper del Valle, zona 1"')
    expect(html).toContain('Estos datos aparecen en cada factura')
    expect(html).toContain('cada una tendrá su propio establecimiento')
  })

  it('solo quien modifica la configuración ve la pantalla (HU-12.15 crit. 3)', () => {
    expect(renderizar(<PaginaEstablecimiento />, CAJERO)).toContain('No tiene permiso para modificar la configuración.')
    expect(renderizar(<PaginaEstablecimiento />)).toContain('aria-label="Establecimiento SAT"')
  })

  it('la configuración enlaza el establecimiento', () => {
    expect(renderizar(<NavConfiguracion actual="Cajas" />)).toContain('href="/configuracion/establecimiento"')
    expect(renderizar(<NavConfiguracion actual="Establecimiento" />)).toContain('<strong>Establecimiento</strong>')
  })

  it('guardar es un PUT con los datos del formulario', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(GUARDADO), { status: 200 })))

    await guardarEstablecimiento({ codigoSat: '1', nombre: 'N', direccion: 'D', codigoPostal: '01001', municipio: 'M',
      departamento: 'G' })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/configuracion/establecimiento')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ codigoSat: '1', nombre: 'N', direccion: 'D', codigoPostal: '01001',
      municipio: 'M', departamento: 'G' }) })
    vi.unstubAllGlobals()
  })
})
