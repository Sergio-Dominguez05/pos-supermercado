import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { activarProducto, desactivarProducto, type Producto } from '../../api/productos'
import { EstadoProducto } from './EditarProducto'
import { avisoEstadoProducto, confirmacionDesactivarProducto } from './textos'

const LECHE: Producto = { id: 4, codigoBarras: '7401234567890', descripcion: 'Leche entera 1 L', precio: 11.2, activo: true, modificadoEn: '2026-11-08T14:05:00Z' }

const renderizar = (producto: Producto) =>
  renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <EstadoProducto producto={producto} />
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('activar y desactivar un producto (HU-03.03)', () => {
  it('un producto activo ofrece desactivarlo (crit. 1)', () => {
    const html = renderizar(LECHE)

    expect(html).toContain('Activo')
    expect(html).toContain('Desactivar producto')
  })

  it('un producto inactivo ofrece reactivarlo (crit. 3)', () => {
    const html = renderizar({ ...LECHE, activo: false })

    expect(html).toContain('Inactivo')
    expect(html).toContain('No se puede agregar a nuevas ventas.')
    expect(html).toContain('Activar producto')
  })

  it('no hay opción de borrar el producto (crit. 2)', () => {
    expect(renderizar(LECHE).toLowerCase()).not.toMatch(/eliminar|borrar/)
  })

  it('la confirmación explica que no se borra y que no se podrá vender', () => {
    const texto = confirmacionDesactivarProducto(LECHE)

    expect(texto).toContain('No se podrá agregar a nuevas ventas')
    expect(texto).toContain('El producto no se borra')
    expect(avisoEstadoProducto({ ...LECHE, activo: false })).toContain('ya no se puede agregar a nuevas ventas')
    expect(avisoEstadoProducto(LECHE)).toContain('ya se puede vender')
  })

  it('activar y desactivar son POST a su ruta', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response(JSON.stringify(LECHE), { status: 200 })))

    await desactivarProducto(4)
    await activarProducto(4)

    const llamadas = vi.mocked(fetch).mock.calls.map(([url, opciones]) => `${opciones?.method} ${url}`)
    expect(llamadas).toEqual(['POST /api/productos/4/desactivar', 'POST /api/productos/4/activar'])
  })
})
