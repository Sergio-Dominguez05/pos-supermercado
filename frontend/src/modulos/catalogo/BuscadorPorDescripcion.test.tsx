import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { buscarPorDescripcion, MINIMO_BUSQUEDA, type Producto } from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { BuscadorPorDescripcion, ResultadosDescripcion } from './BuscadorPorDescripcion'
import { BuscarProducto } from './BuscarProducto'

const producto = (id: number, descripcion: string, precio: number): Producto => ({
  id,
  codigoBarras: `COD-${id}`,
  descripcion,
  precio,
  activo: true,
  modificadoEn: '2026-11-08T14:05:00Z',
})
const RESULTADOS = [producto(1, 'Jabón de tocador', 6.5), producto(2, 'Jabón líquido', 18)]

const renderizar = (contenido: ReactNode) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, { permisos: ['PRODUCTO_CONSULTAR'] } satisfies Partial<UsuarioSesion>)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('buscar producto por descripción (HU-03.07)', () => {
  it('pide al menos 3 caracteres (crit. 1)', () => {
    expect(MINIMO_BUSQUEDA).toBe(3)
    expect(renderizar(<BuscadorPorDescripcion onSeleccionado={() => {}} />)).toContain(
      'placeholder="Escriba al menos 3 letras"',
    )
  })

  it('los resultados muestran código, descripción y precio (crit. 3)', () => {
    const html = renderizar(<ResultadosDescripcion id="r" productos={RESULTADOS} marcado={0} onElegir={() => {}} />)

    expect(html).toContain('COD-1')
    expect(html).toContain('Jabón de tocador')
    expect(html).toContain('Q6.50')
    expect(html).toContain('Q18.00')
  })

  it('el resultado marcado se indica para recorrerlos con el teclado (crit. 4)', () => {
    const html = renderizar(<ResultadosDescripcion id="r" productos={RESULTADOS} marcado={1} onElegir={() => {}} />)

    expect(html).toContain('role="listbox"')
    expect(html).toMatch(/id="r-1" role="option" aria-selected="true"/)
    expect(html).toMatch(/id="r-0" role="option" aria-selected="false"/)
  })

  it('el campo es un combobox accesible que indica cómo usar el teclado (crit. 4)', () => {
    const html = renderizar(<BuscadorPorDescripcion onSeleccionado={() => {}} />)

    expect(html).toContain('role="combobox"')
    expect(html).toContain('Flechas para moverse, Enter para elegir, Escape para limpiar.')
  })

  it('la pantalla de búsqueda tiene los dos buscadores', () => {
    const html = renderizar(<BuscarProducto />)

    expect(html).toContain('aria-label="Buscar por código"')
    expect(html).toContain('aria-label="Buscar por descripción"')
  })

  it('la consulta envía el texto sin espacios sobrantes (crit. 2: el servidor ignora tildes y mayúsculas)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(RESULTADOS), { status: 200 })))

    await buscarPorDescripcion('  café molido ')

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/productos/busqueda?q=caf%C3%A9+molido')
  })
})
