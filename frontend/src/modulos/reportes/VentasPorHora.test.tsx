import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { generarReporteHoras, rutaExportacion, type FilaVentasHora, type ReporteVentasPorHora } from '../../api/reportes'
import { alturaDeBarra, horaCorta } from './filtros'
import { TablaVentasPorHora } from './VentasPorHora'

const fila = (hora: number, cantidad: number, total: number, dias = 1): FilaVentasHora => ({
  hora,
  etiqueta: `${horaCorta(hora)}:00 – ${horaCorta(hora)}:59`,
  cantidadVentas: cantidad,
  totalVendido: total,
  promedioVentas: cantidad / dias,
  promedioTotal: total / dias,
})

const reporte = (dias: number): ReporteVentasPorHora => {
  const filas = Array.from({ length: 24 }, (_, hora) =>
    hora === 8 ? fila(8, 2, 30, dias) : hora === 17 ? fila(17, 6, 120, dias) : fila(hora, 0, 0, dias),
  )
  return {
    encabezado: { titulo: 'Ventas por hora', filtros: [], generadoEn: '2026-11-20T16:30:00Z', generadoPor: 'Ana (ana)' },
    dias,
    promedio: dias > 1,
    filas,
    totales: { ...fila(0, 8, 150, dias), hora: null, etiqueta: 'Total' },
    horaPico: 17,
  }
}

const html = (contenido: React.ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reporte de ventas por hora (HU-10.11)', () => {
  it('muestra la cantidad de ventas y el total de cada hora (crit. 1)', () => {
    const tabla = html(<TablaVentasPorHora reporte={reporte(1)} />)

    expect(tabla).toContain('<h2>Ventas por hora</h2>')
    expect(tabla).toContain('<td>08:00 – 08:59</td><td>2</td><td>Q30.00</td></tr>')
    expect(tabla).toContain('<th scope="row">Total</th><td>8</td><td>Q150.00</td>')
    expect(tabla).not.toContain('Promedio por día')
  })

  it('en un período agrega el promedio por día de cada hora (crit. 2)', () => {
    const tabla = html(<TablaVentasPorHora reporte={reporte(4)} />)

    expect(tabla).toContain('<th>Ventas por día</th><th>Promedio por día</th>')
    expect(tabla).toContain('<td>17:00 – 17:59</td><td>6</td><td>Q120.00</td><td>1.5</td><td>Q30.00</td>')
    expect(tabla).toContain('Ventas promedio por día en cada hora (4 días)')
  })

  it('dibuja una barra por hora y resalta la hora pico (crit. 3)', () => {
    const tabla = html(<TablaVentasPorHora reporte={reporte(1)} />)

    expect(tabla).toContain('role="img" aria-label="Gráfica de barras de las ventas por hora"')
    expect(tabla.match(/class="barra( pico)?"/g)).toHaveLength(24)
    expect(tabla).toContain('class="barra pico" title="17:00 – 17:59: 6 ventas · Q120.00"')
    expect(tabla).toContain('style="height:100%"')
    expect(tabla).toContain('Hora de más ventas: <strong>17:00 – 17:59</strong> (6 ventas)')
    expect(alturaDeBarra(2, 6)).toBe(33)
    expect(alturaDeBarra(0.01, 600)).toBe(2)
    expect(alturaDeBarra(0, 0)).toBe(0)
  })

  it('avisa cuando no hay ventas', () => {
    expect(html(<TablaVentasPorHora reporte={{ ...reporte(1), horaPico: null }} />)).toContain(
      'No hay ventas con estos filtros.',
    )
  })

  it('pide y exporta el reporte con los filtros comunes', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(reporte(1)), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const filtros = {
      desde: '2026-11-20',
      hasta: '2026-11-20',
      cajaId: '3',
      cajeroId: '',
      agrupacion: 'DIA' as const,
      tipoTarjeta: 'DEBITO',
      soloConDiferencias: false,
      anuladaPorId: '',
    }

    await generarReporteHoras(filtros)

    expect((respuesta.mock.calls[0] as unknown[])[0]).toBe('/api/reportes/horas?desde=2026-11-20&hasta=2026-11-20&cajaId=3')
    expect(rutaExportacion({ codigo: 'VENTAS_HORA', filtros }, 'PDF')).toBe(
      '/api/reportes/exportar/VENTAS_HORA?formato=PDF&desde=2026-11-20&hasta=2026-11-20&cajaId=3',
    )
  })
})
