import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import type { Caja } from '../../api/cajas'
import { CLAVE_IMPRESORAS, crearImpresora, probarImpresora, type Impresora } from '../../api/impresoras'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioCaja } from './FormularioCaja'
import { FormularioImpresora, GestionImpresoras, PaginaImpresoras, ResultadoDePrueba } from './Impresoras'
import { NavConfiguracion } from './NavConfiguracion'
import { avisoImpresoraGuardada } from './textos'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }
const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const TERMICA: Impresora = { id: 7, nombre: 'Térmica caja 1', host: '192.168.1.50', puerto: 9100, anchoMm: 80,
  copiasFactura: 2, copiasCierre: 1, modificadoEn: '2026-11-17T03:00:00Z' }
const ANGOSTA: Impresora = { ...TERMICA, id: 8, nombre: 'Angosta', host: 'impresora-2.local', anchoMm: 58 }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_IMPRESORAS, [TERMICA, ANGOSTA])
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''
const radio = (html: string, ancho: number) =>
  html.match(new RegExp(`<input[^>]*name="anchoMm"[^>]*value="${ancho}"[^>]*>`))?.[0] ?? ''
const sinAccion = () => undefined

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('impresoras (HU-12.08)', () => {
  it('lista cada impresora con su dirección, papel, copias y la página de prueba (crit. 1, 2 y 3)', () => {
    const html = renderizar(<GestionImpresoras impresoras={[TERMICA, ANGOSTA]} />)

    for (const columna of ['Impresora', 'Dirección', 'Papel', 'Copias']) expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('192.168.1.50:9100')
    expect(html).toContain('<td>58 mm</td>')
    expect(html).toContain('Factura 2 · Cierre 1')
    expect(html).toContain('aria-label="Imprimir página de prueba en Térmica caja 1"')
    expect(html).toContain('aria-label="Editar Angosta"')
  })

  it('pide la dirección en la red, el ancho de papel y las copias (crit. 1 y 3)', () => {
    const html = renderizar(<FormularioImpresora onGuardada={sinAccion} onCancelar={sinAccion} />)

    for (const nombre of ['nombre', 'host', 'puerto', 'copiasFactura', 'copiasCierre'])
      expect(campo(html, nombre), nombre).toContain('required')
    expect(campo(html, 'puerto')).toContain('value="9100"')
    expect(radio(html, 80)).toContain('checked')
    expect(radio(html, 58)).not.toContain('checked')
    expect(campo(html, 'copiasFactura')).toContain('max="5"')
    expect(html).toContain('Agregar impresora')
  })

  it('al editar carga los datos guardados', () => {
    const html = renderizar(<FormularioImpresora impresora={ANGOSTA} onGuardada={sinAccion} onCancelar={sinAccion} />)

    expect(campo(html, 'host')).toContain('value="impresora-2.local"')
    expect(radio(html, 58)).toContain('checked')
    expect(campo(html, 'copiasFactura')).toContain('value="2"')
    expect(html).toContain('Guardar cambios')
  })

  it('muestra el resultado de la página de prueba, también cuando falla (crit. 2)', () => {
    expect(renderToString(<ResultadoDePrueba resultado={{ exitosa: true, mensaje: 'Página enviada.' }} />))
      .toContain('class="exito"')
    expect(renderToString(<ResultadoDePrueba resultado={{ exitosa: false, mensaje: 'No respondió.' }} />))
      .toContain('role="alert"')
  })

  it('crear y probar llaman a la configuración de impresoras', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{"id":7}', { status: 200 })))

    await crearImpresora({ nombre: 'T', host: '10.0.0.5', puerto: 9100, anchoMm: 58, copiasFactura: 1,
      copiasCierre: 1 })
    await probarImpresora(7)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/configuracion/impresoras')
    expect(JSON.parse(llamadas[0][1]?.body as string)).toMatchObject({ host: '10.0.0.5', anchoMm: 58 })
    expect(llamadas[1][0]).toBe('/api/configuracion/impresoras/7/prueba')
    expect(llamadas[1][1]).toMatchObject({ method: 'POST' })
  })

  it('cada caja elige su impresora (HU-12.05 crit. 2)', () => {
    const caja: Caja = { id: 1, nombre: 'Caja 1', serieFel: null, activo: true, equipoVinculado: false,
      impresoraId: 8, impresoraNombre: 'Angosta' }
    const html = renderizar(<FormularioCaja caja={caja} />)

    expect(html).toContain('<option value="">Sin impresora</option>')
    expect(html).toContain('<option value="7">Térmica caja 1 (80 mm)</option>')
    expect(html).toMatch(/<option value="8" selected="">Angosta \(58 mm\)<\/option>/)
  })

  it('solo quien modifica la configuración la ve, y está en la navegación', () => {
    expect(renderizar(<PaginaImpresoras />, CAJERO)).toContain('No tiene permiso para modificar la configuración.')
    expect(renderizar(<NavConfiguracion actual="Cajas" />)).toContain('href="/configuracion/impresoras"')
    expect(avisoImpresoraGuardada(TERMICA, true)).toBe(
      'Se agregó la impresora Térmica caja 1. Imprima una página de prueba y asígnela a su caja en Cajas.',
    )
  })
})
