import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  generarReporteAnuladas,
  generarReporteCierres,
  type OpcionesDeFiltro,
  type ReporteAnuladas,
  type ReporteCierres,
} from '../../api/reportes'
import { TablaAnuladas } from './Anuladas'
import { TablaCierres } from './Cierres'
import { FormularioFiltros } from './ComunesReporte'
import { textoDiferencia } from './filtros'

const ENCABEZADO = {
  titulo: 'Cierres de caja',
  filtros: [{ etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' }],
  generadoEn: '2026-11-20T16:30:00Z',
  generadoPor: 'Ana Supervisora (ana.sup)',
}

const cierre = (turnoId: number, diferencia: number, justificacion: string | null) => ({
  turnoId,
  abiertoEn: '2026-11-16T14:00:00Z',
  cerradoEn: '2026-11-16T23:00:00Z',
  caja: 'Caja 1',
  cajero: 'Lucía Méndez (lucia.mendez)',
  cerradoPor: 'Lucía Méndez (lucia.mendez)',
  totalRegistrado: 300,
  totalLote: 300 + diferencia,
  diferencia,
  justificacion,
})

const CIERRES: ReporteCierres = {
  encabezado: ENCABEZADO,
  cierres: [cierre(1, 0, null), cierre(2, -9.5, 'Voucher extraviado'), cierre(3, 5, 'Cobro duplicado')],
  cierresConDiferencia: 2,
  totalFaltantes: 9.5,
  totalSobrantes: 5,
}

const ANULADAS: ReporteAnuladas = {
  encabezado: { ...ENCABEZADO, titulo: 'Facturas anuladas' },
  facturas: [
    {
      facturaId: 9,
      emitidaEn: '2026-11-16T15:00:00Z',
      anuladaEn: '2026-11-16T16:00:00Z',
      serie: 'A1',
      numero: '77',
      ventaNumero: 41,
      caja: 'Caja 1',
      monto: 45,
      motivo: 'Error de digitación',
      cajero: 'Lucía Méndez (lucia.mendez)',
      anuladaPor: 'Ana Supervisora (ana.sup)',
    },
  ],
  cantidad: 1,
  monto: 45,
}

const OPCIONES: OpcionesDeFiltro = {
  hoy: '2026-11-20',
  rangos: [],
  cajas: [],
  cajeros: [],
  tiposTarjeta: [],
  agrupaciones: [],
  anuladores: [{ valor: '3', etiqueta: 'Ana Supervisora (ana.sup)' }],
}

const html = (contenido: ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cierres de caja (HU-10.08)', () => {
  it('muestra cada cierre con sus totales, la diferencia y la justificación (crit. 1)', () => {
    const tabla = html(<TablaCierres reporte={CIERRES} />)

    for (const columna of [
      'Cerrado',
      'Caja',
      'Cajero',
      'Registrado con tarjeta',
      'Cierre de lote',
      'Diferencia',
      'Justificación',
    ])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('<td>Q300.00</td><td>Q290.50</td><td class="error">−Q9.50 (faltante)</td>')
    expect(tabla).toContain('<td class="texto-largo">Voucher extraviado</td>')
    expect(tabla).toContain('<td>+Q5.00 (sobrante)</td>')
    expect(tabla).toContain('<td>Q0.00</td><td class="texto-largo">—</td>')
  })

  it('suma los faltantes y los sobrantes del período (crit. 2)', () => {
    const tabla = html(<TablaCierres reporte={CIERRES} />)

    expect(tabla).toContain('<dt>Con diferencia</dt><dd>2</dd>')
    expect(tabla).toContain('<dt>Total de faltantes</dt><dd class="error">Q9.50</dd>')
    expect(tabla).toContain('<dt>Total de sobrantes</dt><dd>Q5.00</dd>')
    expect(textoDiferencia(0)).toBe('Q0.00')
    expect(html(<TablaCierres reporte={{ ...CIERRES, cierres: [] }} />)).toContain(
      'No hay cierres de caja con estos filtros.',
    )
  })

  it('ofrece filtrar solo los cierres con diferencias (crit. 3)', () => {
    expect(html(<FormularioFiltros opciones={OPCIONES} conDiferencias onGenerar={() => {}} />)).toContain(
      '<input type="checkbox" name="soloConDiferencias"/>Solo con diferencias',
    )
    expect(html(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)).not.toContain('Solo con diferencias')
  })
})

describe('facturas anuladas (HU-10.09)', () => {
  it('muestra las fechas, la factura, el monto, el motivo, el cajero y quién anuló (crit. 1)', () => {
    const tabla = html(<TablaAnuladas reporte={ANULADAS} />)

    for (const columna of [
      'Emitida',
      'Anulada',
      'Serie y número',
      'Monto',
      'Motivo',
      'Cajero que emitió',
      'Anulada por',
    ])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('A1-77<small> · venta N.º 41</small>')
    expect(tabla).toContain(
      '<td>Q45.00</td><td class="texto-largo">Error de digitación</td><td>Lucía Méndez (lucia.mendez)</td><td>Ana Supervisora (ana.sup)</td>',
    )
    expect(tabla).toContain('1 factura anulada</th><td>Q45.00</td>')
    expect(html(<TablaAnuladas reporte={{ ...ANULADAS, facturas: [], cantidad: 0, monto: 0 }} />)).toContain(
      'No hay facturas anuladas con estos filtros.',
    )
  })

  it('filtra por quien anuló, solo en este reporte (crit. 2)', () => {
    const conAnulador = html(<FormularioFiltros opciones={OPCIONES} conAnulador onGenerar={() => {}} />)
    expect(conAnulador).toContain('Anulada por')
    expect(conAnulador).toContain(
      '<option value="" selected="">Cualquiera</option><option value="3">Ana Supervisora (ana.sup)</option>',
    )
    expect(html(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)).not.toContain('Anulada por')
  })

  it('pide cada reporte solo con sus filtros', async () => {
    const respuesta = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const filtros = {
      desde: '2026-11-01',
      hasta: '2026-11-20',
      cajaId: '1',
      cajeroId: '',
      agrupacion: 'DIA' as const,
      tipoTarjeta: 'DEBITO',
      soloConDiferencias: true,
      anuladaPorId: '3',
    }

    await generarReporteCierres(filtros)
    await generarReporteCierres({ ...filtros, soloConDiferencias: false })
    await generarReporteAnuladas(filtros)

    expect(respuesta.mock.calls.map((llamada) => (llamada as unknown[])[0])).toEqual([
      '/api/reportes/cierres?desde=2026-11-01&hasta=2026-11-20&cajaId=1&soloConDiferencias=true',
      '/api/reportes/cierres?desde=2026-11-01&hasta=2026-11-20&cajaId=1',
      '/api/reportes/anuladas?desde=2026-11-01&hasta=2026-11-20&cajaId=1&anuladaPorId=3',
    ])
  })
})
