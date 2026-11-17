import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, type Turno } from '../../api/turnos'
import { agregarProducto, CLAVE_VENTA_EN_CURSO, type Venta as DatosVenta } from '../../api/ventas'
import { CLAVE_SESION } from '../login/sesion'
import { BusquedaEnVenta } from './BusquedaEnVenta'
import { Venta } from './Venta'

const CAJERO = {
  rol: 'CAJERO',
  nombreCompleto: 'Ana López',
  rolNombre: 'Cajero',
  permisos: ['VENTA_REGISTRAR', 'CAJA_OPERAR_PROPIA', 'PRODUCTO_CONSULTAR'],
  cajaNombre: 'Caja 1',
} as UsuarioSesion

const TURNO = { id: 7, cajaId: 1, cajaNombre: 'Caja 1', usuarioNombre: 'Ana López', estado: 'ABIERTO',
  abiertoEn: '2026-11-16T14:05:00Z', propio: true } as Turno

const VENTA = { id: 31, numero: 1042, estado: 'EN_CURSO', cajaNombre: 'Caja 1', creadaEn: '2026-11-16T15:00:00Z',
  suspendidaEn: null,
  lineas: [], articulos: 0, subtotal: 0, iva: 0, total: 0 } as DatosVenta

const pantalla = (turno: Turno | null) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, CAJERO)
  cliente.setQueryData(CLAVE_TURNO_ACTUAL, turno)
  cliente.setQueryData(CLAVE_VENTA_EN_CURSO, VENTA)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>
        <Venta />
      </MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('agregar producto por búsqueda o código manual (HU-06.03)', () => {
  it('la venta ofrece el código y la búsqueda por descripción con su atajo F2 (crit. 1, 2 y 4)', () => {
    const html = pantalla(TURNO)

    expect(html).toMatch(/<input[^>]*name="codigo"/)
    expect(html).toMatch(/<input[^>]*name="descripcion"/)
    expect(html).toContain('Buscar por descripción <kbd>F2</kbd>')
    expect(html).toContain('Flechas para moverse, Enter para elegir, Escape para limpiar.')
  })

  it('con la caja cerrada tampoco se puede buscar para agregar', () => {
    expect(pantalla(null).match(/<fieldset[^>]*>/)?.[0]).toContain('disabled')
  })

  it('elegir un producto es un POST con su id a la venta en curso (crit. 2)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(VENTA), { status: 200 })))

    await agregarProducto(5)

    const [ruta, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(ruta).toBe('/api/ventas/actual/lineas')
    expect(opciones).toMatchObject({ method: 'POST', body: '{"productoId":5}' })
  })

  it('el componente de búsqueda de la venta usa el buscador del catálogo', () => {
    const html = renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <BusquedaEnVenta />
      </QueryClientProvider>,
    )

    expect(html).toContain('role="combobox"')
  })
})
