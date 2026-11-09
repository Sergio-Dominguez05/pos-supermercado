import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { claveProducto, editarProducto, type Producto } from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { EditarProducto, FormularioEdicionProducto } from './EditarProducto'
import { avisoProductoEditado } from './textos'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['PRODUCTO_CONSULTAR', 'PRODUCTO_EDITAR'] }
const LECHE: Producto = { id: 4, codigoBarras: '7401234567890', descripcion: 'Leche entera 1 L', precio: 11.2, activo: true }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveProducto(LECHE.id), LECHE)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[`/catalogo/${LECHE.id}/editar`]}>
        <Routes>
          <Route path="/catalogo/:id/editar" element={contenido} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('edición de productos (HU-03.02)', () => {
  it('muestra la descripción y el precio actuales para modificarlos (crit. 1)', () => {
    const html = renderizar(<FormularioEdicionProducto producto={LECHE} />)

    expect(campo(html, 'descripcion')).toContain('value="Leche entera 1 L"')
    expect(campo(html, 'precio')).toContain('value="11.20"')
    expect(html).toContain('Guardar cambios')
  })

  it('el código se muestra pero no se puede modificar (crit. 2)', () => {
    const html = renderizar(<FormularioEdicionProducto producto={LECHE} />)

    expect(campo(html, 'codigoBarras')).toContain('disabled')
    expect(campo(html, 'codigoBarras')).toContain('value="7401234567890"')
    expect(html).toContain('desactive el producto y registre uno nuevo')
  })

  it('carga el producto de la ruta', () => {
    expect(renderizar(<EditarProducto />)).toContain('value="Leche entera 1 L"')
  })

  it('sin el permiso de cambiar precios no muestra el formulario', () => {
    const html = renderizar(<EditarProducto />, { rol: 'SUPERVISOR', permisos: ['PRODUCTO_CONSULTAR'] })

    expect(html).toContain('No tiene permiso para editar productos.')
    expect(html).not.toContain('<form')
  })

  it('el aviso explica que el precio nuevo se aplica desde ahora (crit. 3)', () => {
    expect(avisoProductoEditado({ ...LECHE, precio: 12 })).toBe(
      'Se actualizó Leche entera 1 L (7401234567890): ahora cuesta Q12.00. ' +
        'El precio nuevo se aplica a lo que se agregue a una venta desde ahora.',
    )
  })

  it('la pantalla del producto incluye su estado', () => {
    expect(renderizar(<EditarProducto />)).toContain('Desactivar producto')
  })

  it('la edición es un PUT con descripción y precio, sin el código (crit. 2)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(LECHE), { status: 200 })))

    await editarProducto(4, { descripcion: 'Leche entera 1 L', precio: '12,50' })

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/productos/4')
    expect(opciones?.method).toBe('PUT')
    expect(JSON.parse(opciones?.body as string)).toEqual({ descripcion: 'Leche entera 1 L', precio: 12.5 })
  })
})
