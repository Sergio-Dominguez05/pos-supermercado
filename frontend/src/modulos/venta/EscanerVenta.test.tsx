import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { agregarPorCodigo, type LineaVenta, type Venta } from '../../api/ventas'
import { ConfirmacionAgregado, EscanerVenta } from './EscanerVenta'
import { lineaAgregada } from './lineas'

const ARROZ: LineaVenta = {
  id: 1,
  productoId: 5,
  codigoBarras: '7401',
  descripcion: 'Arroz 1 lb',
  cantidad: 1,
  precioUnitario: 5,
  subtotalConIva: 5,
  baseImponible: 4.464286,
  iva: 0.535714,
}

const AGUA: LineaVenta = { ...ARROZ, id: 2, productoId: 6, codigoBarras: '7402', descripcion: 'Agua 600 ml', precioUnitario: 11.2 }

const venta = (lineas: LineaVenta[], total = 0): Venta => ({
  id: 31,
  numero: 1042,
  estado: 'EN_CURSO',
  cajaNombre: 'Caja 1',
  creadaEn: '2026-11-16T15:00:00Z',
  suspendidaEn: null,
  lineas,
  articulos: lineas.reduce((suma, linea) => suma + linea.cantidad, 0),
  subtotal: 0,
  iva: 0,
  total,
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('agregar producto con lector de código de barras (HU-06.02)', () => {
  it('el campo recibe el foco y el Enter del lector envía el código (crit. 1)', () => {
    const html = renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <EscanerVenta />
      </QueryClientProvider>,
    )

    expect(html).toMatch(/<input[^>]*autofocus=""[^>]*name="codigo"|<input[^>]*name="codigo"[^>]*autofocus=""/i)
    expect(html).toContain('Escanee o escriba el código y presione Enter')
  })

  it('cada escaneo es un POST al servidor con el código', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(venta([ARROZ], 5)), { status: 200 })))

    await expect(agregarPorCodigo('7401')).resolves.toMatchObject({ total: 5 })
    const [ruta, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(ruta).toBe('/api/ventas/actual/lineas')
    expect(opciones).toMatchObject({ method: 'POST', body: '{"codigo":"7401"}' })
  })

  it('identifica la línea nueva que agregó el escaneo', () => {
    expect(lineaAgregada(venta([ARROZ]), venta([ARROZ, AGUA]))).toEqual(AGUA)
  })

  it('identifica la línea que sumó 1 al volver a escanear el mismo producto (crit. 2)', () => {
    expect(lineaAgregada(venta([ARROZ, AGUA]), venta([{ ...ARROZ, cantidad: 2 }, AGUA]))).toMatchObject({
      id: 1,
      cantidad: 2,
    })
  })

  it('confirma de forma visible lo que se agregó (crit. 3)', () => {
    const html = renderToString(
      <ConfirmacionAgregado linea={{ ...ARROZ, cantidad: 2 }} venta={venta([{ ...ARROZ, cantidad: 2 }], 10)} />,
    ).replaceAll('<!-- -->', '')

    expect(html).toContain('role="status"')
    expect(html).toContain('Agregado: Arroz 1 lb · Q5.00 (2 en la venta) · Total Q10.00')
  })
})
