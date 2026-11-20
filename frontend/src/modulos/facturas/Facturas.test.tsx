import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { listarReimprimibles, reimprimirFactura, reimprimirUltima, type FacturaEmitida } from '../../api/facturas'
import { ACCIONES_PENDIENTES, ATAJOS } from '../../atajos/atajos'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { ListaReimprimibles, PaginaFacturas } from './Facturas'

const SIN_PERMISO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const FACTURA: FacturaEmitida = { facturaId: 7, ventaId: 31, ventaNumero: 1042, turnoId: 3, turnoAbierto: true,
  cajaNombre: 'Caja 1', estado: 'CERTIFICADA', serie: 'SIM', numero: '25',
  autorizacion: '5b2c1f0e-1a2b-4c3d-9e8f-001122334455', fechaCertificacion: '2026-11-19T16:15:30Z',
  emitidaEn: '2026-11-19T16:15:29Z', total: 15, receptorId: '5769370', receptorNombre: 'Ferretería El Clavo',
  motivoAnulacion: null, anuladaEn: null }

const renderizar = (contenido: ReactNode, sesion = SIN_PERMISO) => {
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

describe('reimprimir una factura (HU-08.12)', () => {
  it('F9 reimprime la última factura: ya no está pendiente (crit. 1)', () => {
    expect(ATAJOS.find((atajo) => atajo.tecla === 'F9')?.accion).toBe('reimprimirUltima')
    expect(ACCIONES_PENDIENTES.reimprimirUltima).toBeUndefined()
  })

  it('lista las facturas que se pueden reimprimir con su botón (crit. 2)', () => {
    const html = renderizar(<ListaReimprimibles facturas={[FACTURA]} />)

    for (const columna of ['Factura', 'Emitida', 'Venta y caja', 'Cliente', 'Total'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('SIM-25')
    expect(html).toContain('NIT 576937-0 · Ferretería El Clavo')
    expect(html).toContain('aria-label="Reimprimir la factura SIM-25"')
    expect(renderizar(<ListaReimprimibles facturas={[]} />)).toContain('No se encontraron facturas.')
  })

  it('la opción Facturas del menú lleva a la búsqueda, y sin permiso no se muestra', () => {
    expect(opcionesPara(['FACTURA_REIMPRIMIR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Facturas', ruta: '/facturas' }),
    ])
    expect(renderizar(<PaginaFacturas />)).toContain('No tiene permiso para reimprimir facturas.')
  })

  it('busca y reimprime con las rutas del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await listarReimprimibles('25', '')
    await reimprimirUltima()
    await reimprimirFactura(7)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/facturas/reimprimibles?buscar=25')
    expect(llamadas[1][0]).toBe('/api/facturas/ultima/reimprimir')
    expect(llamadas[2][0]).toBe('/api/facturas/7/reimprimir')
    expect(llamadas[2][1]).toMatchObject({ method: 'POST' })
  })
})
