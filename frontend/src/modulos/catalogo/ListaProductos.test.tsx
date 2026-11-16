import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  claveListaProductos,
  FILTRO_PRODUCTOS_INICIAL,
  listarProductos,
  type PaginaProductos,
  type Producto,
} from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { ListaProductos } from './ListaProductos'
import { formatoFecha, resumenPagina } from './textos'

const LECHE: Producto = {
  id: 4,
  codigoBarras: '7401234567890',
  descripcion: 'Leche entera 1 L',
  precio: 11.2,
  activo: true,
  modificadoEn: '2026-11-08T14:05:00Z',
}
const PAN: Producto = { ...LECHE, id: 5, codigoBarras: 'PAN-01', descripcion: 'Pan francés', precio: 1, activo: false }
const PAGINA: PaginaProductos = { productos: [LECHE, PAN], pagina: 0, tamano: 25, total: 60 }

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['PRODUCTO_CONSULTAR', 'PRODUCTO_EDITAR'] }
const SUPERVISOR: Partial<UsuarioSesion> = { rol: 'SUPERVISOR', permisos: ['PRODUCTO_CONSULTAR'] }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveListaProductos(FILTRO_PRODUCTOS_INICIAL), PAGINA)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('lista del catálogo (HU-03.04)', () => {
  it('muestra código, descripción, precio, estado y fecha de modificación (crit. 1)', () => {
    const html = renderizar(<ListaProductos />)

    for (const columna of ['Código', 'Descripción', 'Precio', 'Estado', 'Última modificación']) {
      expect(html).toContain(`<th>${columna}</th>`)
    }
    expect(html).toContain('<td>7401234567890</td>')
    expect(html).toContain('<td>Q11.20</td>')
    expect(html).toContain('Inactivo')
    expect(html).toContain(`<td>${formatoFecha(LECHE.modificadoEn)}</td>`)
  })

  it('ofrece buscar, filtrar por estado y ordenar (crit. 2, 3 y 4)', () => {
    const html = renderizar(<ListaProductos />)

    expect(html).toContain('placeholder="Código o descripción"')
    expect(html).toMatch(/<select[^>]*name="activo"/)
    for (const orden of ['DESCRIPCION:asc', 'PRECIO:desc', 'MODIFICADO:desc']) expect(html).toContain(`value="${orden}"`)
  })

  it('pagina la lista (crit. 5)', () => {
    const html = renderizar(<ListaProductos />)

    expect(html).toContain('Página 1 de 3 · 60 productos')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Anterior/)
    expect(html).toMatch(/<button(?![^>]*disabled)[^>]*>Siguiente/)
  })

  it('con permiso de edición ofrece crear y editar (crit. 6)', () => {
    const html = renderizar(<ListaProductos />)

    expect(html).toContain('href="/catalogo/nuevo"')
    expect(html).toContain('href="/catalogo/4/editar"')
  })

  it('quien solo consulta ve la lista sin crear ni editar (crit. 6)', () => {
    const html = renderizar(<ListaProductos />, SUPERVISOR)

    expect(html).toContain('Leche entera 1 L')
    expect(html).not.toContain('/catalogo/nuevo')
    expect(html).not.toContain('/editar')
  })

  it('sin permiso de consulta no muestra la lista', () => {
    const html = renderizar(<ListaProductos />, { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] })

    expect(html).toContain('No tiene permiso para consultar el catálogo.')
    expect(html).not.toContain('<table')
  })

  it('el resumen de páginas cuenta los productos', () => {
    expect(resumenPagina(1, 25, 60)).toBe('Página 2 de 3 · 60 productos')
    expect(resumenPagina(0, 25, 1)).toBe('Página 1 de 1 · 1 producto')
    expect(resumenPagina(0, 25, 0)).toBe('0 productos')
  })

  it('la consulta envía la búsqueda, el estado, el orden y la página', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(PAGINA), { status: 200 })))

    await listarProductos({ q: ' leche ', activo: 'false', orden: 'PRECIO', descendente: true, pagina: 2 })

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      '/api/productos?q=leche&activo=false&orden=PRECIO&descendente=true&pagina=2',
    )
  })
})
