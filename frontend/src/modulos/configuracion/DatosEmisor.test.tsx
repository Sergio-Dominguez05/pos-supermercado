import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_EMISOR, guardarEmisor, type Emisor } from '../../api/emisor'
import { nitValido, normalizarNit, verificadorNit } from '../clientes/nit'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioEmisor, PaginaEmisor } from './DatosEmisor'
import { NavConfiguracion } from './NavConfiguracion'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }
const SUPERVISOR: Partial<UsuarioSesion> = { rol: 'SUPERVISOR', permisos: ['FACTURA_ANULAR'] }

const SIN_CONFIGURAR: Emisor = { configurado: false, nit: null, razonSocial: null, nombreComercial: null,
  direccionFiscal: null, telefono: null, correo: null, afiliacionIva: null, modificadoEn: null }
const GUARDADO: Emisor = { configurado: true, nit: '576937K', razonSocial: 'Supermercados del Valle, S.A.',
  nombreComercial: 'Súper del Valle', direccionFiscal: 'Zona 1, Guatemala', telefono: '2222-3333',
  correo: 'facturas@delvalle.com', afiliacionIva: 'GEN', modificadoEn: '2026-11-17T03:00:00Z' }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_EMISOR, GUARDADO)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('NIT (HU-05.03)', () => {
  it('el dígito verificador sigue el módulo 11 de la SAT, con K para 10 (crit. 1)', () => {
    expect(verificadorNit('123456')).toBe('0')
    expect(verificadorNit('1234567')).toBe('9')
    expect(verificadorNit('576937')).toBe('K')
  })

  it('acepta los NIT bien escritos y rechaza los demás (crit. 1 y 2)', () => {
    for (const nit of ['1234560', '12345679', '576937-K', '576937k', '48003']) expect(nitValido(nit), nit).toBe(true)
    for (const nit of ['1234561', '576937', 'K', '', 'CF', '12A4560']) expect(nitValido(nit), nit).toBe(false)
  })

  it('se guarda sin guiones ni espacios y en mayúsculas (crit. 3)', () => {
    expect(normalizarNit(' 576937-k ')).toBe('576937K')
  })
})

describe('datos del emisor (HU-12.01)', () => {
  it('pide NIT, razón social, nombre comercial, dirección, teléfono y correo (crit. 1)', () => {
    const html = renderizar(<FormularioEmisor guardado={SIN_CONFIGURAR} />)

    for (const nombre of ['nit', 'razonSocial', 'nombreComercial', 'direccionFiscal'])
      expect(campo(html, nombre), nombre).toContain('required')
    for (const nombre of ['telefono', 'correo']) expect(campo(html, nombre), nombre).not.toContain('required')
    expect(campo(html, 'correo')).toContain('type="email"')
  })

  it('pide la afiliación al IVA que exige el DTE (HU-08.02)', () => {
    const html = renderizar(<FormularioEmisor guardado={SIN_CONFIGURAR} />)

    expect(html).toContain('<legend>Afiliación al IVA</legend>')
    const general = (contenido: string) => contenido.match(/<input[^>]*value="GEN"[^>]*>/)?.[0] ?? ''
    expect(general(html)).toContain('required')
    expect(html).toContain('value="PEQ"')
    expect(general(renderizar(<FormularioEmisor guardado={GUARDADO} />))).toContain('checked')
  })

  it('carga los datos guardados', () => {
    const html = renderizar(<FormularioEmisor guardado={GUARDADO} />)

    expect(campo(html, 'nit')).toContain('value="576937K"')
    expect(campo(html, 'razonSocial')).toContain('value="Supermercados del Valle, S.A."')
  })

  it('avisa que un cambio no altera las facturas ya emitidas (crit. 4)', () => {
    expect(renderizar(<FormularioEmisor guardado={GUARDADO} />)).toContain(
      'un cambio aquí no altera las facturas ya emitidas',
    )
  })

  it('solo quien modifica la configuración ve la pantalla (HU-12.15 crit. 3)', () => {
    expect(renderizar(<PaginaEmisor />, SUPERVISOR)).toContain('No tiene permiso para modificar la configuración.')
    expect(renderizar(<PaginaEmisor />)).toContain('aria-label="Datos del emisor"')
  })

  it('la configuración enlaza las cajas, el emisor y la conexión FEL', () => {
    const html = renderizar(<NavConfiguracion actual="Emisor" />)

    expect(html).toContain('href="/configuracion/cajas"')
    expect(html).toContain('href="/configuracion/fel"')
    expect(html).toContain('<strong>Emisor</strong>')
    expect(html).not.toContain('href="/configuracion/emisor"')
  })

  it('guardar es un PUT con los datos del formulario', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(GUARDADO), { status: 200 })))

    await guardarEmisor({ nit: '576937K', razonSocial: 'R', nombreComercial: 'N', direccionFiscal: 'D', telefono: '',
      correo: '' })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/configuracion/emisor')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'PUT' })
    vi.unstubAllGlobals()
  })
})
