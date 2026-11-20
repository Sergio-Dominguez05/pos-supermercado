import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  CLAVE_FACTURAS_RECHAZADAS,
  reenviarFacturaDesdeCaja,
  reenviarFacturaRechazada,
  type FacturaRechazada,
} from '../../api/facturas'
import type { FacturaDeVenta } from '../../api/pagos'
import { AvisoRechazadas } from '../inicio/Inicio'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { CorregirFactura } from './CorregirFactura'
import { ListaRechazadas, PaginaFacturasRechazadas } from './FacturasRechazadas'
import { AVISO_SUPERVISOR, rotuloRechazadas } from './textos'

const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const POR_RECEPTOR: FacturaDeVenta = { estado: 'RECHAZADA', autorizacion: null, serie: null, numero: null,
  certificadaEn: null, motivo: 'El NIT o el nombre del cliente no es válido para la SAT.', corregible: true }
const POR_MONTOS: FacturaDeVenta = { ...POR_RECEPTOR, motivo: 'Los montos de la factura no coinciden.', corregible: false }

const RECHAZADA: FacturaRechazada = { ventaId: 31, ventaNumero: 1042, cajaNombre: 'Caja 1', cajero: 'Ana López',
  emitidaEn: '2026-11-19T16:15:30Z', total: 15, receptorId: '5769370', receptorNombre: 'Ferretería El Clavo',
  tipo: 'MONTOS', motivo: 'Los montos de la factura no coinciden.', corregibleEnCaja: false, codigo: 'SIM-002',
  mensajeCertificador: 'El total no cuadra.', respuesta: '{"resultado":false}', rechazadaEn: '2026-11-19T16:15:31Z' }

const renderizar = (contenido: ReactNode, rechazadas: FacturaRechazada[] = [], sesion = CAJERO) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_FACTURAS_RECHAZADAS, rechazadas)
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

describe('rechazos del certificador (HU-08.06)', () => {
  it('un error del cliente se corrige en caja: otro NIT o CF, sin volver a cobrar (crit. 1 y 2)', () => {
    const html = renderizar(<CorregirFactura ventaId={31} numero={1042} factura={POR_RECEPTOR} onTerminado={sinAccion} />)

    expect(html).toContain('Factura de la venta N.º 1042 rechazada')
    expect(html).toContain('El NIT o el nombre del cliente no es válido para la SAT.')
    expect(html).toContain('name="nitCorregido"')
    expect(html).toContain('Reenviar como CF')
    expect(html).not.toContain(AVISO_SUPERVISOR)
  })

  it('un error que no se corrige en caja queda para el supervisor (crit. 3)', () => {
    const html = renderizar(<CorregirFactura ventaId={31} numero={1042} factura={POR_MONTOS} onTerminado={sinAccion} />)

    expect(html).toContain('Los montos de la factura no coinciden.')
    expect(html).toContain('Avise a su supervisor')
    expect(html).not.toContain('name="nitCorregido"')
  })

  it('el supervisor ve cada venta pendiente con su motivo y la respuesta del certificador (crit. 3 y 4)', () => {
    const html = renderizar(<ListaRechazadas rechazadas={[RECHAZADA]} />)

    for (const columna of ['Venta', 'Fecha', 'Caja y cajero', 'Cliente', 'Total', 'Motivo'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('N.º 1042')
    expect(html).toContain('Caja 1 · Ana López')
    expect(html).toContain('NIT 576937-0 · Ferretería El Clavo')
    expect(html).toContain('<strong>Montos:</strong> Los montos de la factura no coinciden.')
    expect(html).toContain('<summary>Respuesta del certificador</summary>')
    expect(html).toContain('SIM-002 · El total no cuadra.')
    expect(html).toContain('aria-label="Reenviar la factura de la venta 1042"')
    expect(renderizar(<ListaRechazadas rechazadas={[]} />)).toContain('No hay ventas pendientes de facturar.')
  })

  it('se avisa en Inicio cuántas ventas esperan su factura, y hay una opción en el menú', () => {
    expect(renderizar(<AvisoRechazadas />, [RECHAZADA])).toContain('Hay una venta pendiente de facturar')
    expect(renderizar(<AvisoRechazadas />, [RECHAZADA, { ...RECHAZADA, ventaId: 32 }])).toContain(
      'Facturas rechazadas (2)',
    )
    expect(renderizar(<AvisoRechazadas />, [])).toBe('')
    expect(rotuloRechazadas(0)).toBe('Facturas rechazadas')
    expect(opcionesPara(['FACTURA_ANULAR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Facturas rechazadas', ruta: '/facturas/rechazadas' }),
      expect.objectContaining({ etiqueta: 'Anular facturas', ruta: '/facturas/anular' }),
      expect.objectContaining({ etiqueta: 'Contingencia', ruta: '/facturas/contingencia' }),
    ])
  })

  it('solo quien gestiona facturas ve la lista', () => {
    expect(renderizar(<PaginaFacturasRechazadas />)).toContain('No tiene permiso para gestionar las facturas rechazadas.')
  })

  it('reenviar usa la ruta del cajero o la del supervisor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await reenviarFacturaDesdeCaja(31, 5)
    await reenviarFacturaRechazada(31)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/ventas/31/factura/reenviar')
    expect(JSON.parse(llamadas[0][1]?.body as string)).toEqual({ clienteId: 5 })
    expect(llamadas[1][0]).toBe('/api/facturas/rechazadas/31/reenviar')
    expect(llamadas[1][1]).toMatchObject({ method: 'POST' })
  })
})
