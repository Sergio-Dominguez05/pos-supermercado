import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  CLAVE_PARAMETROS_OPERACION,
  guardarParametrosOperacion,
  type ParametrosOperacion,
} from '../../api/parametrosOperacion'
import { CLAVE_SESION } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { FormularioParametros, PaginaParametrosOperacion } from './ParametrosOperacion'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }
const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }
const GUARDADOS: ParametrosOperacion = { cantidadMaximaPorLinea: 50, cierreCiego: true, limiteConsumidorFinal: 2500 }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_PARAMETROS_OPERACION, GUARDADOS)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('parámetros de caja y venta (HU-12.13)', () => {
  it('muestra la cantidad guardada y permite activar el cierre ciego', () => {
    const html = renderizar(<FormularioParametros guardado={GUARDADOS} />)

    expect(campo(html, 'cantidadMaximaPorLinea')).toContain('type="number"')
    expect(campo(html, 'cantidadMaximaPorLinea')).toContain('min="1"')
    expect(campo(html, 'cantidadMaximaPorLinea')).toContain('max="9999"')
    expect(campo(html, 'cantidadMaximaPorLinea')).toContain('value="50"')
    expect(campo(html, 'cierreCiego')).toContain('checked=""')
    expect(html).toContain('sin ver primero el total registrado')
  })

  it('solo quien modifica la configuración ve el formulario', () => {
    expect(renderizar(<PaginaParametrosOperacion />, CAJERO))
      .toContain('No tiene permiso para modificar la configuración.')
    expect(renderizar(<PaginaParametrosOperacion />)).toContain('aria-label="Parámetros de caja y venta"')
  })

  it('la navegación enlaza la sección de caja y venta', () => {
    expect(renderizar(<NavConfiguracion actual="Cajas" />)).toContain('href="/configuracion/parametros"')
    expect(renderizar(<NavConfiguracion actual="Caja y venta" />)).toContain('<strong>Caja y venta</strong>')
  })

  it('guardar usa PUT con ambos parámetros', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(GUARDADOS), { status: 200 })))

    await guardarParametrosOperacion(GUARDADOS)

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/configuracion/parametros')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'PUT', body: JSON.stringify(GUARDADOS) })
    vi.unstubAllGlobals()
  })

  it('el límite para Consumidor Final se configura en quetzales (HU-12.07 crit. 1)', () => {
    const html = renderizar(<FormularioParametros guardado={GUARDADOS} />)
    const limite = html.match(/<input[^>]*name="limiteConsumidorFinal"[^>]*>/)?.[0] ?? ''

    expect(limite).toContain('value="2500.00"')
    expect(limite).toContain('step="0.01"')
    expect(html).toContain('Límite para Consumidor Final (Q)')
  })
})
