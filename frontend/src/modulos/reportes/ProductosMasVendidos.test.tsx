import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  generarReporteProductos,
  rutaExportacion,
  type OpcionesDeFiltro,
  type ReporteProductosMasVendidos,
} from '../../api/reportes'
import { FormularioFiltros } from './ComunesReporte'
import { TablaProductosMasVendidos } from './ProductosMasVendidos'

const REPORTE: ReporteProductosMasVendidos = {
  encabezado: {
    titulo: 'Productos más vendidos',
    filtros: [
      { etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' },
      { etiqueta: 'Ordenado por', valor: 'Cantidad vendida' },
      { etiqueta: 'Mostrar', valor: 'Los 2 primeros' },
    ],
    generadoEn: '2026-11-20T16:30:00Z',
    generadoPor: 'Ana Admin (ana.admin)',
  },
  ordenarPor: 'CANTIDAD',
  limite: 2,
  productosVendidos: 5,
  filas: [
    { posicion: 1, productoId: 11, codigo: '750200', descripcion: 'Frijol 1 lb', cantidad: 8, monto: 40 },
    { posicion: 2, productoId: 10, codigo: '750100', descripcion: 'Arroz 1 lb', cantidad: 4, monto: 44.8 },
  ],
  totales: { posicion: null, productoId: null, codigo: null, descripcion: 'Total', cantidad: 12, monto: 84.8 },
}

const OPCIONES: OpcionesDeFiltro = {
  hoy: '2026-11-20',
  rangos: [{ codigo: 'ESTE_MES', etiqueta: 'Este mes', desde: '2026-11-01', hasta: '2026-11-20' }],
  cajas: [],
  cajeros: [],
  tiposTarjeta: [],
  agrupaciones: [],
  anuladores: [],
}

const FILTROS = {
  desde: '2026-11-01',
  hasta: '2026-11-20',
  cajaId: '2',
  cajeroId: '',
  agrupacion: 'MES' as const,
  tipoTarjeta: 'CREDITO',
  soloConDiferencias: true,
  anuladaPorId: '3',
  ordenarPor: 'MONTO' as const,
  limite: '50',
}

const html = (contenido: React.ReactNode) => renderToString(contenido).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('reporte de productos más vendidos (HU-10.05)', () => {
  it('muestra por producto el código, la descripción, la cantidad y el monto (crit. 1)', () => {
    const tabla = html(<TablaProductosMasVendidos reporte={REPORTE} />)

    expect(tabla).toContain('<h2>Productos más vendidos</h2>')
    for (const columna of ['N.º', 'Código', 'Descripción']) expect(tabla).toContain(`<th>${columna}</th>`)
    expect(tabla).toContain('<td>1</td><td>750200</td><td>Frijol 1 lb</td><td>8</td><td>Q40.00</td>')
    expect(tabla).toContain('<td>2</td><td>750100</td><td>Arroz 1 lb</td><td>4</td><td>Q44.80</td>')
  })

  it('marca la columna por la que se ordenó (crit. 2)', () => {
    expect(html(<TablaProductosMasVendidos reporte={REPORTE} />)).toContain(
      '<th aria-sort="descending">Cantidad vendida</th><th>Monto vendido</th>',
    )
    expect(html(<TablaProductosMasVendidos reporte={{ ...REPORTE, ordenarPor: 'MONTO' }} />)).toContain(
      '<th>Cantidad vendida</th><th aria-sort="descending">Monto vendido</th>',
    )
  })

  it('avisa cuando la lista está recortada y suma solo los mostrados (crit. 3)', () => {
    const tabla = html(<TablaProductosMasVendidos reporte={REPORTE} />)

    expect(tabla).toContain('Se muestran los 2 primeros de 5 productos vendidos.')
    expect(tabla).toContain('Total de los 2 mostrados</th><td>12</td><td>Q84.80</td>')
    const completo = html(<TablaProductosMasVendidos reporte={{ ...REPORTE, productosVendidos: 2 }} />)
    expect(completo).toContain('Se vendieron 2 productos en el período.')
    expect(completo).toContain('>Total</th>')
  })

  it('avisa cuando no se vendió nada', () => {
    expect(
      html(<TablaProductosMasVendidos reporte={{ ...REPORTE, filas: [], productosVendidos: 0 }} />),
    ).toContain('No se vendieron productos con estos filtros.')
  })

  it('el formulario ofrece el orden y el límite, con los 20 primeros por cantidad al abrir (crit. 2 y 3)', () => {
    const formulario = html(<FormularioFiltros opciones={OPCIONES} conProductos onGenerar={() => {}} />)

    expect(formulario).toContain('Ordenar por')
    expect(formulario).toContain('<option value="CANTIDAD" selected="">Cantidad vendida</option>')
    expect(formulario).toContain('<option value="MONTO">Monto vendido</option>')
    expect(formulario).toContain('<option value="20" selected="">Los 20 primeros</option>')
    expect(formulario).toContain('<option value="50">Los 50 primeros</option>')
    expect(html(<FormularioFiltros opciones={OPCIONES} onGenerar={() => {}} />)).not.toContain('Ordenar por')
  })

  it('pide y exporta el reporte con el orden y el límite, sin los filtros de otros reportes', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(REPORTE), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await generarReporteProductos(FILTROS)

    expect((respuesta.mock.calls[0] as unknown[])[0]).toBe(
      '/api/reportes/productos?desde=2026-11-01&hasta=2026-11-20&cajaId=2&ordenarPor=MONTO&limite=50',
    )
    expect(rutaExportacion({ codigo: 'PRODUCTOS', filtros: FILTROS }, 'PDF')).toBe(
      '/api/reportes/exportar/PRODUCTOS?formato=PDF&desde=2026-11-01&hasta=2026-11-20&cajaId=2&ordenarPor=MONTO&limite=50',
    )
  })
})
