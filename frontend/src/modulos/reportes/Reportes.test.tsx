import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  CLAVE_FILTROS_REPORTE,
  consultaDe,
  generarReporteVentas,
  type OpcionesDeFiltro,
  type ReporteVentasPeriodo,
} from '../../api/reportes'
import { CLAVE_SESION } from '../login/sesion'
import { filtrosIniciales } from './filtros'
import { FormularioFiltros } from './ComunesReporte'
import { PaginaReportes, SelectorDeReportes } from './Reportes'
import { TablaVentasPeriodo } from './VentasPeriodo'

const SUPERVISOR: Partial<UsuarioSesion> = {
  rol: 'SUPERVISOR',
  permisos: ['REPORTE_CONSULTAR'],
}
const CAJERO: Partial<UsuarioSesion> = {
  rol: 'CAJERO',
  permisos: ['VENTA_REGISTRAR'],
}

const OPCIONES: OpcionesDeFiltro = {
  hoy: '2026-11-20',
  rangos: [
    {
      codigo: 'HOY',
      etiqueta: 'Hoy',
      desde: '2026-11-20',
      hasta: '2026-11-20',
    },
    {
      codigo: 'AYER',
      etiqueta: 'Ayer',
      desde: '2026-11-19',
      hasta: '2026-11-19',
    },
    {
      codigo: 'ESTA_SEMANA',
      etiqueta: 'Esta semana',
      desde: '2026-11-16',
      hasta: '2026-11-20',
    },
    {
      codigo: 'ESTE_MES',
      etiqueta: 'Este mes',
      desde: '2026-11-01',
      hasta: '2026-11-20',
    },
    {
      codigo: 'MES_ANTERIOR',
      etiqueta: 'Mes anterior',
      desde: '2026-10-01',
      hasta: '2026-10-31',
    },
  ],
  cajas: [{ valor: '1', etiqueta: 'Caja 1' }],
  cajeros: [{ valor: '7', etiqueta: 'Lucía Méndez (lucia.mendez)' }],
  tiposTarjeta: [
    { valor: 'DEBITO', etiqueta: 'Débito' },
    { valor: 'CREDITO', etiqueta: 'Crédito' },
  ],
  agrupaciones: [
    { valor: 'DIA', etiqueta: 'Día' },
    { valor: 'SEMANA', etiqueta: 'Semana' },
    { valor: 'MES', etiqueta: 'Mes' },
  ],
  anuladores: [{ valor: '3', etiqueta: 'Ana Supervisora (ana.sup)' }],
}

const fila = (periodo: string, desde: string, cantidadVentas: number, total: number) => ({
  periodo,
  desde,
  hasta: desde,
  cantidadVentas,
  subtotal: Math.round((total / 1.12) * 100) / 100,
  iva: Math.round((total - total / 1.12) * 100) / 100,
  total,
  ticketPromedio: cantidadVentas === 0 ? 0 : Math.round((total / cantidadVentas) * 100) / 100,
})

const REPORTE: ReporteVentasPeriodo = {
  encabezado: {
    titulo: 'Ventas por período',
    filtros: [
      { etiqueta: 'Período', valor: 'Del 01/11/2026 al 02/11/2026' },
      { etiqueta: 'Caja', valor: 'Caja 1' },
      { etiqueta: 'Cajero', valor: 'Todos' },
      { etiqueta: 'Agrupación', valor: 'Día' },
    ],
    generadoEn: '2026-11-20T16:30:00Z',
    generadoPor: 'Ana Supervisora (ana.sup)',
  },
  agrupacion: 'DIA',
  filas: [fila('01/11/2026', '2026-11-01', 2, 22.4), fila('02/11/2026', '2026-11-02', 0, 0)],
  totales: { ...fila('Total', '2026-11-01', 2, 22.4), hasta: '2026-11-02' },
}

const renderizar = (contenido: ReactNode, sesion = SUPERVISOR) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_FILTROS_REPORTE, OPCIONES)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reportes (HU-10.01, HU-10.02, HU-10.15)', () => {
  it('el supervisor ve los filtros; el cajero no tiene acceso (HU-10.15 crit. 1 y 2)', () => {
    const supervisor = renderizar(<PaginaReportes />)
    expect(supervisor).toContain('Filtros del reporte')
    expect(supervisor).toContain('Generar reporte')
    // Una pestaña por reporte; abre en ventas por período, con su agrupación.
    expect(supervisor).toContain('role="tab" aria-selected="true">Ventas por período</button>')
    expect(supervisor).toContain('role="tab" aria-selected="false">Ventas por cajero</button>')
    expect(supervisor).toContain('Agrupar por')
    expect(renderizar(<SelectorDeReportes opciones={OPCIONES} inicial="VENTAS_CAJERO" />)).not.toContain('Agrupar por')

    const cajero = renderizar(<PaginaReportes />, CAJERO)
    expect(cajero).toContain('No tiene permiso para consultar reportes.')
    expect(cajero).not.toContain('Filtros del reporte')
  })

  it('ofrece los rangos rápidos y abre en el mes en curso (HU-10.01 crit. 1 y 3)', () => {
    const html = renderizar(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)

    for (const rango of ['Hoy', 'Ayer', 'Esta semana', 'Este mes', 'Mes anterior'])
      expect(html).toContain(`>${rango}</button>`)
    expect(html).toContain('aria-pressed="true">Este mes</button>')
    expect(html).toContain('aria-pressed="false">Hoy</button>')
    expect(html).toMatch(/required="" name="desde" value="2026-11-01"/)
    expect(html).toMatch(/required="" name="hasta" value="2026-11-20"/)
    expect(filtrosIniciales(OPCIONES)).toEqual({
      desde: '2026-11-01',
      hasta: '2026-11-20',
      cajaId: '',
      cajeroId: '',
      agrupacion: 'DIA',
      tipoTarjeta: '',
      soloConDiferencias: false,
      anuladaPorId: '',
      ordenarPor: 'CANTIDAD',
      limite: '20',
    })
  })

  it('filtra por caja y cajero, y agrupa por día, semana o mes (HU-10.01 crit. 2, HU-10.02 crit. 2)', () => {
    const html = renderizar(<FormularioFiltros opciones={OPCIONES} conAgrupacion onGenerar={() => {}} />)

    expect(html).toContain('<option value="" selected="">Todas</option><option value="1">Caja 1</option>')
    expect(html).toContain('<option value="7">Lucía Méndez (lucia.mendez)</option>')
    for (const agrupacion of ['Día', 'Semana', 'Mes']) expect(html).toContain(`>${agrupacion}</option>`)
    // Los reportes que no agrupan no muestran ese filtro.
    expect(renderizar(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)).not.toContain('Agrupar por')
  })

  it('muestra los filtros aplicados en el encabezado y las columnas del reporte (HU-10.01 crit. 4, HU-10.02 crit. 1)', () => {
    const html = renderizar(<TablaVentasPeriodo reporte={REPORTE} />)

    expect(html).toContain('<h2>Ventas por período</h2>')
    expect(html).toContain('<strong>Período:</strong> Del 01/11/2026 al 02/11/2026')
    expect(html).toContain('<strong>Caja:</strong> Caja 1')
    expect(html).toContain('<strong>Cajero:</strong> Todos')
    expect(html).toContain('por Ana Supervisora (ana.sup)')
    for (const columna of ['Período', 'Ventas', 'Subtotal', 'IVA', 'Total vendido', 'Ticket promedio'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('<td>01/11/2026</td><td>2</td><td>Q20.00</td><td>Q2.40</td><td>Q22.40</td><td>Q11.20</td>')
    expect(html).toContain('<td>02/11/2026</td><td>0</td>')
    expect(html).toContain('<tfoot><tr><th scope="row">Total</th><td>2</td>')
    expect(html).toContain('Las facturas anuladas no se suman a las ventas.')
    expect(html).not.toContain('No hay ventas con estos filtros.')
  })

  it('avisa cuando no hay ventas con los filtros elegidos', () => {
    const vacio = {
      ...REPORTE,
      filas: [REPORTE.filas[1]],
      totales: { ...REPORTE.totales, cantidadVentas: 0 },
    }
    expect(renderizar(<TablaVentasPeriodo reporte={vacio} />)).toContain('No hay ventas con estos filtros.')
  })

  it('pide el reporte solo con los filtros que tienen valor', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(REPORTE), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await generarReporteVentas({
      desde: '2026-11-01',
      hasta: '2026-11-02',
      cajaId: '1',
      cajeroId: '',
      agrupacion: 'MES',
      tipoTarjeta: 'CREDITO',
      soloConDiferencias: true,
      anuladaPorId: '3',
    })

    expect(respuesta).toHaveBeenCalledWith(
      '/api/reportes/ventas?desde=2026-11-01&hasta=2026-11-02&cajaId=1&agrupacion=MES',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(consultaDe({ desde: '', cajeroId: '7' })).toBe('cajeroId=7')
  })
})
