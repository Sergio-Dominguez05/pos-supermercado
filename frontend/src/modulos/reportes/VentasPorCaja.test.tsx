import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { generarReporteCajas, rutaExportacion, type ReporteVentasPorCaja } from '../../api/reportes'
import { textoParticipacion } from './filtros'
import { TablaVentasPorCaja } from './VentasPorCaja'

const REPORTE: ReporteVentasPorCaja = {
  encabezado: {
    titulo: 'Ventas por caja',
    filtros: [
      { etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' },
      { etiqueta: 'Caja', valor: 'Todas' },
      { etiqueta: 'Cajero', valor: 'Todos' },
    ],
    generadoEn: '2026-11-20T16:30:00Z',
    generadoPor: 'Ana Supervisora (ana.sup)',
  },
  filas: [
    {
      cajaId: 2,
      caja: 'Caja 2',
      cantidadVentas: 30,
      totalVendido: 750,
      turnos: 4,
      ticketPromedio: 25,
      participacion: 75,
    },
    {
      cajaId: 1,
      caja: 'Caja 1',
      cantidadVentas: 10,
      totalVendido: 250,
      turnos: 2,
      ticketPromedio: 25,
      participacion: 25,
    },
  ],
  totales: {
    cajaId: null,
    caja: 'Total',
    cantidadVentas: 40,
    totalVendido: 1000,
    turnos: 6,
    ticketPromedio: 25,
    participacion: 100,
  },
}

const FILTROS = {
  desde: '2026-11-01',
  hasta: '2026-11-20',
  cajaId: '',
  cajeroId: '8',
  agrupacion: 'SEMANA' as const,
  tipoTarjeta: 'DEBITO',
  soloConDiferencias: true,
  anuladaPorId: '3',
}

const html = (contenido: React.ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reporte de ventas por caja (HU-10.04)', () => {
  it('muestra por caja las ventas, el total vendido y los turnos (crit. 1)', () => {
    const tabla = html(<TablaVentasPorCaja reporte={REPORTE} />)

    expect(tabla).toContain('<h2>Ventas por caja</h2>')
    for (const columna of ['Caja', 'Ventas', 'Total vendido', 'Turnos', 'Ticket promedio', 'Participación'])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('<td>Caja 2</td><td>30</td><td>Q750.00</td><td>4</td><td>Q25.00</td>')
    expect(tabla).toContain('<tfoot><tr><th scope="row">Total</th><td>40</td><td>Q1000.00</td><td>6</td>')
  })

  it('compara las cajas en el mismo período con su participación, en el orden del servidor (crit. 2)', () => {
    const tabla = html(<TablaVentasPorCaja reporte={REPORTE} />)

    expect(tabla.indexOf('Caja 2')).toBeLessThan(tabla.indexOf('Caja 1'))
    expect(tabla).toContain('aria-label="Participación de Caja 2"')
    expect(tabla).toContain('75.0 %')
    expect(tabla).toContain('25.0 %')
    expect(tabla).toContain('<td>100.0 %</td>')
    expect(textoParticipacion(66.7)).toBe('66.7 %')
  })

  it('avisa cuando no hay cajas', () => {
    expect(html(<TablaVentasPorCaja reporte={{ ...REPORTE, filas: [] }} />)).toContain('No hay cajas con estos filtros.')
  })

  it('pide el reporte y lo exporta con los filtros comunes, sin agrupación ni tarjeta', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(REPORTE), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await generarReporteCajas(FILTROS)

    expect((respuesta.mock.calls[0] as unknown[])[0]).toBe(
      '/api/reportes/cajas?desde=2026-11-01&hasta=2026-11-20&cajeroId=8',
    )
    expect(rutaExportacion({ codigo: 'VENTAS_CAJA', filtros: FILTROS }, 'XLSX')).toBe(
      '/api/reportes/exportar/VENTAS_CAJA?formato=XLSX&desde=2026-11-01&hasta=2026-11-20&cajeroId=8',
    )
  })
})
