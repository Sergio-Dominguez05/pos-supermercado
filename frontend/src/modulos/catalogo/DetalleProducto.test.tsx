import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  claveHistorialPrecios,
  claveProducto,
  obtenerHistorialPrecios,
  type CambioDePrecio,
  type Producto,
} from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { DetalleProducto, HistorialPrecios } from './DetalleProducto'
import { formatoFecha } from './textos'

const LECHE: Producto = {
  id: 9,
  codigoBarras: '7401234',
  descripcion: 'Leche entera 1 L',
  precio: 12.25,
  activo: true,
  modificadoEn: '2026-11-20T15:00:00Z',
}

const CAMBIOS: CambioDePrecio[] = [
  { cambiadoEn: '2026-11-20T15:00:00Z', anterior: 11.5, nuevo: 12.25, usuario: 'Ana Admin (admin)' },
  { cambiadoEn: '2026-11-19T15:00:00Z', anterior: 10, nuevo: 11.5, usuario: 'Ana Admin (admin)' },
]

const renderizar = (contenido: ReactNode, permisos: string[], datos?: (cliente: QueryClient) => void) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, { rol: 'SUPERVISOR', permisos } satisfies Partial<UsuarioSesion>)
  datos?.(cliente)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={['/catalogo/9']}>
        <Routes>
          <Route path="/catalogo/:id" element={contenido} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('historial de precios (HU-03.11)', () => {
  it('muestra fecha, precio anterior, nuevo y quién lo cambió, del más reciente (crit. 1 y 2)', () => {
    const html = renderizar(<HistorialPrecios cambios={CAMBIOS} />, ['BITACORA_CONSULTAR'])

    for (const columna of ['Fecha y hora', 'Precio anterior', 'Precio nuevo', 'Cambiado por'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain(
      `<td>${formatoFecha('2026-11-20T15:00:00Z')}</td><td>Q11.50</td><td>Q12.25</td><td>Ana Admin (admin)</td>`,
    )
    expect(html.indexOf('Q12.25')).toBeLessThan(html.indexOf('Q10.00'))
    expect(html).toContain('estos registros no se pueden modificar')
    expect(html).not.toContain('<button')
    expect(renderizar(<HistorialPrecios cambios={[]} />, ['BITACORA_CONSULTAR'])).toContain(
      'El precio no ha cambiado desde que se registró el producto.',
    )
  })

  it('el Supervisor ve el historial en el detalle; el Cajero solo los datos del producto', () => {
    const conDatos = (cliente: QueryClient) => {
      cliente.setQueryData(claveProducto(9), LECHE)
      cliente.setQueryData(claveHistorialPrecios(9), CAMBIOS)
    }
    const supervisor = renderizar(<DetalleProducto />, ['PRODUCTO_CONSULTAR', 'BITACORA_CONSULTAR'], conDatos)
    expect(supervisor).toContain('<h2>Leche entera 1 L</h2>')
    expect(supervisor).toContain('<h2>Historial de precios</h2>')
    expect(supervisor).not.toContain('>Editar</a>')

    const cajero = renderizar(<DetalleProducto />, ['PRODUCTO_CONSULTAR'], conDatos)
    expect(cajero).toContain('<h2>Leche entera 1 L</h2>')
    expect(cajero).not.toContain('Historial de precios')
  })

  it('consulta el historial con la ruta del servidor', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('[]', { status: 200 })),
    )
    await obtenerHistorialPrecios(9)
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/productos/9/precios')
  })
})
