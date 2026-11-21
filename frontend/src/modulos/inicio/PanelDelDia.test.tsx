import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { INTERVALO_PANEL_MS, type PanelDelDia } from '../../api/reportes'
import { ResumenDelDia } from './PanelDelDia'
import { alertasDelPanel } from './textos'

const PANEL: PanelDelDia = {
  fecha: '2026-11-20',
  actualizadoEn: '2026-11-20T16:30:00Z',
  cantidadVentas: 42,
  totalVendido: 3150.5,
  ticketPromedio: 75.01,
  cajasAbiertas: [{ turnoId: 9, caja: 'Caja 1', cajero: 'Lucía Méndez (lucia)', abiertaDesde: '2026-11-20T13:00:00Z' }],
  ventasPorTarjeta: [
    { tipo: 'DEBITO', etiqueta: 'Débito', transacciones: 30, monto: 2000 },
    { tipo: 'CREDITO', etiqueta: 'Crédito', transacciones: 12, monto: 1150.5 },
  ],
  alertas: { contingenciaPendientes: 2, codigosNoEncontrados: 1, cierresConDiferencia: 0 },
}

const html = (contenido: ReactNode) =>
  renderToString(<MemoryRouter>{contenido}</MemoryRouter>).replaceAll('<!-- -->', '')

describe('panel resumen del día (HU-10.12)', () => {
  it('muestra el total, las ventas, el ticket promedio, las cajas abiertas y las tarjetas (crit. 1)', () => {
    const panel = html(<ResumenDelDia panel={PANEL} />)

    expect(panel).toContain('<dt>Total vendido</dt><dd>Q3150.50</dd>')
    expect(panel).toContain('<dt>Ventas</dt><dd>42</dd>')
    expect(panel).toContain('<dt>Ticket promedio</dt><dd>Q75.01</dd>')
    expect(panel).toContain('<dt>Cajas abiertas</dt><dd>1</dd>')
    expect(panel).toContain('<dt>Débito</dt><dd>Q2000.00 <small>(30)</small></dd>')
    expect(panel).toContain('<dt>Crédito</dt><dd>Q1150.50 <small>(12)</small></dd>')
    expect(panel).toContain('Caja 1 · Lucía Méndez (lucia) · desde')
  })

  it('muestra solo las alertas pendientes, cada una con dónde atenderla (crit. 2)', () => {
    const panel = html(<ResumenDelDia panel={PANEL} />)

    expect(panel).toContain('2 comprobantes en contingencia pendientes de certificar. <a href="/facturas/contingencia"')
    expect(panel).toContain('1 código no encontrado en caja hoy. <a href="/catalogo"')
    expect(panel).not.toContain('cierre')
    expect(alertasDelPanel({ contingenciaPendientes: 0, codigosNoEncontrados: 0, cierresConDiferencia: 3 })).toEqual([
      { texto: '3 cierres de caja de hoy con diferencia.', ruta: '/caja/cierres' },
    ])
    expect(
      html(<ResumenDelDia panel={{ ...PANEL, alertas: { contingenciaPendientes: 0, codigosNoEncontrados: 0, cierresConDiferencia: 0 } }} />),
    ).not.toContain('aria-label="Alertas"')
  })

  it('se actualiza solo cada minuto y dice cuándo (crit. 3)', () => {
    expect(INTERVALO_PANEL_MS).toBe(60_000)
    expect(html(<ResumenDelDia panel={PANEL} />)).toContain('se actualiza solo cada minuto')
  })
})
