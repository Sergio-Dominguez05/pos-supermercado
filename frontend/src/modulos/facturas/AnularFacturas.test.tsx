import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { anularFactura, buscarFacturas, type FacturaEmitida } from '../../api/facturas'
import { CLAVE_SESION } from '../login/sesion'
import { EstadoFactura, PaginaAnularFacturas, ResultadosFacturas } from './AnularFacturas'
import { avisoAnulada, confirmacionAnular } from './textos'

const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const CERTIFICADA: FacturaEmitida = { facturaId: 7, ventaId: 31, ventaNumero: 1042, turnoId: 3, turnoAbierto: true,
  cajaNombre: 'Caja 1', estado: 'CERTIFICADA', serie: 'SIM', numero: '25',
  autorizacion: '5b2c1f0e-1a2b-4c3d-9e8f-001122334455', fechaCertificacion: '2026-11-19T16:15:30Z',
  emitidaEn: '2026-11-19T16:15:29Z', total: 15, receptorId: 'CF', receptorNombre: 'Consumidor Final',
  motivoAnulacion: null, anuladaEn: null }

const renderizar = (contenido: ReactNode, sesion = CAJERO) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}
const sinAccion = () => undefined

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('anular una factura (HU-08.10)', () => {
  it('muestra cada factura con su serie, número, autorización, venta y estado (crit. 1)', () => {
    const html = renderizar(<ResultadosFacturas facturas={[CERTIFICADA]} onAnulada={sinAccion} />)

    for (const columna of ['Factura', 'Autorización', 'Emitida', 'Venta y caja', 'Cliente', 'Total', 'Estado'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('SIM-25')
    expect(html).toContain('5B2C1F0E-1A2B-4C3D-9E8F-001122334455')
    expect(html).toContain('N.º 1042 · Caja 1')
    expect(html).toContain('aria-label="Anular la factura 25"')
    expect(renderizar(<ResultadosFacturas facturas={[]} onAnulada={sinAccion} />)).toContain(
      'No se encontraron facturas.',
    )
  })

  it('una anulada muestra su motivo y con el turno cerrado ya no se ofrece (crit. 4 y 6)', () => {
    const anulada = renderizar(
      <EstadoFactura factura={{ ...CERTIFICADA, estado: 'ANULADA', motivoAnulacion: 'Error de digitación' }}
        onAnulada={sinAccion} />,
    )
    const cerrada = renderizar(<EstadoFactura factura={{ ...CERTIFICADA, turnoAbierto: false }} onAnulada={sinAccion} />)

    expect(anulada).toContain('<strong>Anulada</strong>')
    expect(anulada).toContain('Error de digitación')
    expect(cerrada).toContain('turno cerrado, no se puede anular')
    expect(cerrada).not.toContain('Anular</button>')
  })

  it('la confirmación dice qué se anula y que no se puede deshacer (crit. 2 y 3)', () => {
    expect(confirmacionAnular('SIM', '25', 1042)).toBe(
      '¿Anular la factura SIM-25 de la venta N.º 1042? La anulación se envía a la SAT y no se puede deshacer.',
    )
    expect(avisoAnulada('SIM', '25')).toBe('La factura SIM-25 quedó anulada.')
  })

  it('solo quien puede anular ve la pantalla (crit. 5)', () => {
    expect(renderizar(<PaginaAnularFacturas />)).toContain('No tiene permiso para anular facturas.')
  })

  it('busca y anula con las rutas del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('[]', { status: 200 })))

    await buscarFacturas(' 25 ', '2026-11-19')
    await buscarFacturas('', '')
    await anularFactura(7, 'Error de digitación')

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/facturas?buscar=25&fecha=2026-11-19')
    expect(llamadas[1][0]).toBe('/api/facturas')
    expect(llamadas[2][0]).toBe('/api/facturas/7/anular')
    expect(JSON.parse(llamadas[2][1]?.body as string)).toEqual({ motivo: 'Error de digitación' })
  })
})
