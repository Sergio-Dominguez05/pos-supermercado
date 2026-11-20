import { renderToString } from 'react-dom/server'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  generarDetalleIva,
  generarDetalleTarjeta,
  generarReporteIva,
  generarReporteTarjetas,
  type DetalleFacturasIva,
  type DetallePagosTarjeta,
  type OpcionesDeFiltro,
  type ReporteIva,
  type ReporteTarjetas,
} from '../../api/reportes'
import { FormularioFiltros } from './ComunesReporte'
import { TablaFacturasIva, TablaIva } from './Iva'
import { TablaDetalleTarjeta, TablaTarjetas } from './Tarjetas'

const ENCABEZADO = {
  titulo: 'Ingresos por tipo de tarjeta',
  filtros: [{ etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' }],
  generadoEn: '2026-11-20T16:30:00Z',
  generadoPor: 'Ana Supervisora (ana.sup)',
}

const TARJETAS: ReporteTarjetas = {
  encabezado: ENCABEZADO,
  filas: [
    { tipo: 'DEBITO', etiqueta: 'Débito', transacciones: 3, monto: 150 },
    { tipo: 'CREDITO', etiqueta: 'Crédito', transacciones: 1, monto: 40.5 },
  ],
  total: { tipo: null, etiqueta: 'Total', transacciones: 4, monto: 190.5 },
  anulados: [
    { tipo: 'DEBITO', etiqueta: 'Débito', transacciones: 1, monto: 25 },
    { tipo: 'CREDITO', etiqueta: 'Crédito', transacciones: 0, monto: 0 },
  ],
  totalAnulado: { tipo: null, etiqueta: 'Total', transacciones: 1, monto: 25 },
}

const PAGOS: DetallePagosTarjeta = {
  encabezado: { ...ENCABEZADO, titulo: 'Detalle de transacciones con tarjeta' },
  pagos: [
    {
      pagoId: 1,
      fecha: '2026-11-16T16:00:00Z',
      ventaNumero: 41,
      caja: 'Caja 1',
      cajero: 'Lucía Méndez (lucia.mendez)',
      autorizacion: 'AUT-001',
      ultimos4: '4242',
      monto: 25,
      anulada: true,
    },
  ],
}

const IVA: ReporteIva = {
  encabezado: { ...ENCABEZADO, titulo: 'IVA cobrado' },
  dias: [{ fecha: '2026-11-16', facturas: 2, baseImponible: 150, iva: 18, total: 168 }],
  total: { fecha: null, facturas: 2, baseImponible: 150, iva: 18, total: 168 },
  sinCertificar: { facturas: 2, total: 41.2 },
}

const FACTURAS: DetalleFacturasIva = {
  encabezado: { ...ENCABEZADO, titulo: 'Detalle de IVA por factura' },
  facturas: [
    {
      facturaId: 9,
      emitidaEn: '2026-11-16T16:00:00Z',
      serie: 'A1',
      numero: '77',
      autorizacion: '6f1c2a10-0000-4000-8000-000000000001',
      receptorId: 'CF',
      receptorNombre: 'Consumidor Final',
      caja: 'Caja 1',
      baseImponible: 100,
      iva: 12,
      total: 112,
    },
  ],
}

const OPCIONES: OpcionesDeFiltro = {
  hoy: '2026-11-20',
  rangos: [],
  cajas: [],
  cajeros: [],
  tiposTarjeta: [
    { valor: 'DEBITO', etiqueta: 'Débito' },
    { valor: 'CREDITO', etiqueta: 'Crédito' },
  ],
  agrupaciones: [],
}

const html = (contenido: ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ingresos por tipo de tarjeta (HU-10.06)', () => {
  it('muestra transacciones y total por tipo, con las anuladas aparte (crit. 1 y 2)', () => {
    const tabla = html(<TablaTarjetas reporte={TARJETAS} onVerDetalle={() => {}} />)

    for (const columna of ['Tipo de tarjeta', 'Transacciones', 'Total recibido', 'De facturas anuladas'])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('<td>Débito</td><td>3</td><td>Q150.00</td><td>1 · Q25.00</td>')
    expect(tabla).toContain('<td>Crédito</td><td>1</td><td>Q40.50</td><td>—</td>')
    expect(tabla).toContain('<th scope="row">Total</th><td>4</td><td>Q190.50</td><td>1 · Q25.00</td>')
    expect(tabla).toContain('aria-label="Ver las transacciones con tarjeta de débito"')
  })

  it('el filtro de tipo de tarjeta solo aparece en los reportes que lo usan (HU-10.01 crit. 2)', () => {
    const conTarjeta = html(<FormularioFiltros opciones={OPCIONES} conTarjeta onGenerar={() => {}} />)
    expect(conTarjeta).toContain('Tipo de tarjeta')
    expect(conTarjeta).toContain('<option value="DEBITO">Débito</option><option value="CREDITO">Crédito</option>')
    expect(html(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)).not.toContain('Tipo de tarjeta')
  })

  it('el detalle lista cada transacción con su autorización y marca las anuladas (crit. 3)', () => {
    const tabla = html(<TablaDetalleTarjeta detalle={PAGOS} />)

    expect(tabla).toContain('<td>N.º 41</td><td>Caja 1</td><td>Lucía Méndez (lucia.mendez)</td><td>AUT-001</td>')
    expect(tabla).toContain('<td>•••• 4242</td>')
    expect(tabla).toContain('Q25.00<small class="error"> · factura anulada</small>')
    expect(html(<TablaDetalleTarjeta detalle={{ ...PAGOS, pagos: [] }} />)).toContain(
      'No hay transacciones con estos filtros.',
    )
  })
})

describe('IVA cobrado (HU-10.07)', () => {
  it('muestra base imponible, IVA y total por día, y avisa las facturas sin certificar (crit. 1, 2 y 4)', () => {
    const tabla = html(<TablaIva reporte={IVA} onVerFacturas={() => {}} />)

    for (const columna of ['Día', 'Facturas', 'Base imponible', 'IVA', 'Total vendido'])
      expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('<td>16/11/2026</td><td>2</td><td>Q150.00</td><td>Q18.00</td><td>Q168.00</td>')
    expect(tabla).toContain('Hay 2 facturas del período sin certificar (Q41.20)')
    expect(tabla).toContain('Ver el detalle por factura')
    const sinPendientes = html(
      <TablaIva reporte={{ ...IVA, sinCertificar: { facturas: 0, total: 0 } }} onVerFacturas={() => {}} />,
    )
    expect(sinPendientes).not.toContain('sin certificar')
  })

  it('el detalle lista cada factura certificada (crit. 3)', () => {
    const tabla = html(<TablaFacturasIva detalle={FACTURAS} />)

    expect(tabla).toContain('<td>A1-77</td>')
    expect(tabla).toContain('<small>6f1c2a10-0000-4000-8000-000000000001</small>')
    expect(tabla).toContain(
      '<td>CF · Consumidor Final</td><td>Caja 1</td><td>Q100.00</td><td>Q12.00</td><td>Q112.00</td>',
    )
  })

  it('pide cada reporte solo con los filtros que le aplican', async () => {
    const respuesta = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const filtros = { desde: '2026-11-01', hasta: '2026-11-20', cajaId: '', cajeroId: '7', agrupacion: 'MES' as const }

    await generarReporteTarjetas({ ...filtros, tipoTarjeta: 'CREDITO' })
    await generarDetalleTarjeta('DEBITO', { ...filtros, tipoTarjeta: 'CREDITO' })
    await generarReporteIva({ ...filtros, tipoTarjeta: 'CREDITO' })
    await generarDetalleIva({ ...filtros, tipoTarjeta: '' })

    expect(respuesta.mock.calls.map((llamada) => (llamada as unknown[])[0])).toEqual([
      '/api/reportes/tarjetas?desde=2026-11-01&hasta=2026-11-20&cajeroId=7&tipoTarjeta=CREDITO',
      '/api/reportes/tarjetas/DEBITO/pagos?desde=2026-11-01&hasta=2026-11-20&cajeroId=7',
      '/api/reportes/iva?desde=2026-11-01&hasta=2026-11-20&cajeroId=7',
      '/api/reportes/iva/facturas?desde=2026-11-01&hasta=2026-11-20&cajeroId=7',
    ])
  })
})
