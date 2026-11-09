import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { buscarPorCodigo, type Producto } from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { BuscadorPorCodigo, ResultadoProducto } from './BuscadorPorCodigo'
import { BuscarProducto } from './BuscarProducto'

const LECHE: Producto = {
  id: 4,
  codigoBarras: '7401234567890',
  descripcion: 'Leche entera 1 L',
  precio: 11.2,
  activo: true,
  modificadoEn: '2026-11-08T14:05:00Z',
}

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = { permisos: ['PRODUCTO_CONSULTAR'] }) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('buscar producto por código (HU-03.06)', () => {
  it('el campo tiene el foco para escanear directamente y el Enter del lector busca (crit. 2)', () => {
    const html = renderizar(<BuscadorPorCodigo />)

    expect(html.match(/<input[^>]*name="codigo"[^>]*>/)?.[0]).toMatch(/autofocus/i)
    expect(html).toContain('role="search"')
    expect(html).toContain('presione Enter')
  })

  it('el resultado muestra código, descripción y precio', () => {
    const html = renderizar(<ResultadoProducto producto={LECHE} />)

    expect(html).toContain('<dd>7401234567890</dd>')
    expect(html).toContain('<dd>Leche entera 1 L</dd>')
    expect(html).toContain('<dd>Q11.20</dd>')
    expect(html).not.toContain('Inactivo')
  })

  it('un producto inactivo se marca como tal', () => {
    expect(renderizar(<ResultadoProducto producto={{ ...LECHE, activo: false }} />)).toContain('<dd>Inactivo</dd>')
  })

  it('la pantalla de búsqueda la ve quien consulta el catálogo, incluido el cajero', () => {
    expect(renderizar(<BuscarProducto />)).toContain('aria-label="Buscar por código"')
    expect(renderizar(<BuscarProducto />, { permisos: ['VENTA_REGISTRAR'] })).toContain(
      'No tiene permiso para consultar el catálogo.',
    )
  })

  it('el código escrito a mano se envía sin espacios y codificado (crit. 3 y 4)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(LECHE), { status: 200 })))

    await buscarPorCodigo('  ABC-12 ')

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/productos/codigo/ABC-12')
  })

  it('un código inexistente llega como error con el mensaje del servidor', async () => {
    const error = { codigo: 'PRODUCTO_NO_ENCONTRADO', mensaje: 'No hay ningún producto con el código 999.', detalles: [] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(error), { status: 404 })))

    await expect(buscarPorCodigo('999')).rejects.toMatchObject({ estado: 404, codigo: 'PRODUCTO_NO_ENCONTRADO' })
  })
})
