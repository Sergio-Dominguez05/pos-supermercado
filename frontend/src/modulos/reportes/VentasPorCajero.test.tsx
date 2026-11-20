import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  generarDetalleCajero,
  generarReporteCajeros,
  type DetalleVentasCajero,
  type ReporteVentasPorCajero,
} from '../../api/reportes'
import { TablaDetalleCajero, TablaVentasPorCajero } from './VentasPorCajero'

const ENCABEZADO = {
  titulo: 'Ventas por cajero',
  filtros: [
    { etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' },
    { etiqueta: 'Caja', valor: 'Todas' },
    { etiqueta: 'Cajero', valor: 'Todos' },
  ],
  generadoEn: '2026-11-20T16:30:00Z',
  generadoPor: 'Ana Supervisora (ana.sup)',
}

const REPORTE: ReporteVentasPorCajero = {
  encabezado: ENCABEZADO,
  filas: [
    {
      cajeroId: 7,
      cajero: 'Lucía Méndez (lucia.mendez)',
      cantidadVentas: 12,
      totalVendido: 450.5,
      ventasCanceladas: 2,
      lineasEliminadas: 5,
      facturasAnuladas: 1,
      montoAnulado: 30,
    },
    {
      cajeroId: 8,
      cajero: 'Luis Pérez (luis.perez)',
      cantidadVentas: 3,
      totalVendido: 60,
      ventasCanceladas: 0,
      lineasEliminadas: 0,
      facturasAnuladas: 0,
      montoAnulado: 0,
    },
  ],
  totales: {
    cajeroId: null,
    cajero: 'Total',
    cantidadVentas: 15,
    totalVendido: 510.5,
    ventasCanceladas: 2,
    lineasEliminadas: 5,
    facturasAnuladas: 1,
    montoAnulado: 30,
  },
}

const DETALLE: DetalleVentasCajero = {
  encabezado: { ...ENCABEZADO, titulo: 'Detalle de ventas del cajero' },
  ventas: [
    {
      ventaId: 40,
      numero: 1040,
      fecha: '2026-11-16T15:00:00Z',
      caja: 'Caja 1',
      estado: 'CANCELADA',
      total: null,
      estadoFactura: null,
      factura: null,
      lineasEliminadas: 1,
    },
    {
      ventaId: 41,
      numero: 1041,
      fecha: '2026-11-16T16:00:00Z',
      caja: 'Caja 1',
      estado: 'FINALIZADA',
      total: 30,
      estadoFactura: 'ANULADA',
      factura: 'A1-77',
      lineasEliminadas: 0,
    },
  ],
}

const html = (contenido: React.ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reporte de ventas por cajero (HU-10.03)', () => {
  it('muestra por cajero las ventas, el total, las canceladas, las líneas eliminadas y las anuladas (crit. 1)', () => {
    const tabla = html(<TablaVentasPorCajero reporte={REPORTE} onVerDetalle={() => {}} />)

    expect(tabla).toContain('<h2>Ventas por cajero</h2>')
    for (const columna of ['Cajero', 'Ventas', 'Total vendido', 'Canceladas', 'Líneas eliminadas', 'Facturas anuladas'])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain(
      '<td>Lucía Méndez (lucia.mendez)</td><td>12</td><td>Q450.50</td><td>2</td><td>5</td><td>1<small> · Q30.00</small></td>',
    )
    expect(tabla).toContain('<td>Luis Pérez (luis.perez)</td><td>3</td><td>Q60.00</td><td>0</td><td>0</td><td>0</td>')
    expect(tabla).toContain('<tfoot><tr><th scope="row">Total</th><td>15</td><td>Q510.50</td>')
    expect(tabla).toContain('aria-label="Ver las ventas de Lucía Méndez (lucia.mendez)"')
  })

  it('avisa cuando ningún cajero tuvo actividad', () => {
    expect(html(<TablaVentasPorCajero reporte={{ ...REPORTE, filas: [] }} onVerDetalle={() => {}} />)).toContain(
      'No hay actividad de cajeros con estos filtros.',
    )
  })

  it('el detalle lista las ventas con su estado, su factura y sus líneas eliminadas (crit. 2)', () => {
    const tabla = html(<TablaDetalleCajero detalle={DETALLE} />)

    expect(tabla).toContain('<h2>Detalle de ventas del cajero</h2>')
    expect(tabla).toContain('<td>N.º 1040</td>')
    expect(tabla).toContain('<td>Cancelada</td><td>—</td><td>—</td><td>1</td>')
    expect(tabla).toContain('<td>Finalizada</td><td>Q30.00</td><td>A1-77 · Anulada</td><td>0</td>')
    expect(html(<TablaDetalleCajero detalle={{ ...DETALLE, ventas: [] }} />)).toContain(
      'El cajero no tiene ventas finalizadas ni canceladas con estos filtros.',
    )
  })

  it('pide el resumen sin agrupación y el detalle con el cajero en la ruta', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(REPORTE), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const filtros = {
      desde: '2026-11-01',
      hasta: '2026-11-20',
      cajaId: '1',
      cajeroId: '8',
      agrupacion: 'DIA' as const,
      tipoTarjeta: 'DEBITO',
    }

    await generarReporteCajeros(filtros)
    await generarDetalleCajero(7, filtros)

    expect(respuesta.mock.calls.map((llamada) => (llamada as unknown[])[0])).toEqual([
      '/api/reportes/cajeros?desde=2026-11-01&hasta=2026-11-20&cajaId=1&cajeroId=8',
      '/api/reportes/cajeros/7/ventas?desde=2026-11-01&hasta=2026-11-20&cajaId=1',
    ])
  })
})
