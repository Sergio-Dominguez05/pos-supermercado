import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  descargarReporte,
  nombreDeArchivo,
  rutaExportacion,
  type EncabezadoReporte,
  type FiltrosVentas,
} from '../../api/reportes'
import { EncabezadoDeReporte } from './ComunesReporte'

const FILTROS: FiltrosVentas = {
  desde: '2026-11-01',
  hasta: '2026-11-20',
  cajaId: '1',
  cajeroId: '',
  agrupacion: 'SEMANA',
  tipoTarjeta: 'CREDITO',
  soloConDiferencias: true,
  anuladaPorId: '3',
}

const ENCABEZADO: EncabezadoReporte = {
  titulo: 'Ventas por período',
  filtros: [{ etiqueta: 'Período', valor: 'Del 01/11/2026 al 20/11/2026' }],
  generadoEn: '2026-11-20T16:30:00Z',
  generadoPor: 'Ana Supervisora (ana.sup)',
}

const html = (contenido: ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('exportar reportes (HU-10.13, HU-10.14)', () => {
  it('cada reporte ofrece exportar a PDF y a Excel (crit. 1)', () => {
    const conBotones = html(
      <EncabezadoDeReporte encabezado={ENCABEZADO} exportacion={{ codigo: 'VENTAS_PERIODO', filtros: FILTROS }} />,
    )

    expect(conBotones).toContain('aria-label="Exportar el reporte"')
    expect(conBotones).toContain('>Exportar a PDF</button>')
    expect(conBotones).toContain('>Exportar a Excel</button>')
    expect(html(<EncabezadoDeReporte encabezado={ENCABEZADO} />)).not.toContain('Exportar a PDF')
  })

  it('exporta con los mismos filtros que la pantalla, solo los que aplican a cada reporte (HU-10.13 crit. 3)', () => {
    const base = 'desde=2026-11-01&hasta=2026-11-20&cajaId=1'
    expect(rutaExportacion({ codigo: 'VENTAS_PERIODO', filtros: FILTROS }, 'PDF')).toBe(
      `/api/reportes/exportar/VENTAS_PERIODO?formato=PDF&${base}&agrupacion=SEMANA`,
    )
    expect(rutaExportacion({ codigo: 'VENTAS_CAJERO', filtros: FILTROS }, 'XLSX')).toBe(
      `/api/reportes/exportar/VENTAS_CAJERO?formato=XLSX&${base}`,
    )
    expect(rutaExportacion({ codigo: 'DETALLE_CAJERO', filtros: FILTROS, cajeroId: 7 }, 'PDF')).toBe(
      `/api/reportes/exportar/DETALLE_CAJERO?formato=PDF&${base}&cajeroId=7`,
    )
    expect(rutaExportacion({ codigo: 'TARJETAS', filtros: FILTROS }, 'PDF')).toBe(
      `/api/reportes/exportar/TARJETAS?formato=PDF&${base}&tipoTarjeta=CREDITO`,
    )
    expect(rutaExportacion({ codigo: 'DETALLE_TARJETA', filtros: FILTROS, tipoTarjeta: 'DEBITO' }, 'PDF')).toBe(
      `/api/reportes/exportar/DETALLE_TARJETA?formato=PDF&${base}&tipoTarjeta=DEBITO`,
    )
    expect(rutaExportacion({ codigo: 'IVA', filtros: FILTROS }, 'XLSX')).toBe(
      `/api/reportes/exportar/IVA?formato=XLSX&${base}`,
    )
    expect(rutaExportacion({ codigo: 'CIERRES', filtros: FILTROS }, 'PDF')).toBe(
      `/api/reportes/exportar/CIERRES?formato=PDF&${base}&soloConDiferencias=true`,
    )
    expect(rutaExportacion({ codigo: 'ANULADAS', filtros: FILTROS }, 'PDF')).toBe(
      `/api/reportes/exportar/ANULADAS?formato=PDF&${base}&anuladaPorId=3`,
    )
  })

  it('toma el nombre del archivo que da el servidor', () => {
    expect(nombreDeArchivo('attachment; filename="iva-cobrado_2026-11-01_2026-11-20.xlsx"', 'XLSX')).toBe(
      'iva-cobrado_2026-11-01_2026-11-20.xlsx',
    )
    expect(nombreDeArchivo(null, 'PDF')).toBe('reporte.pdf')
  })

  it('descarga el archivo con el nombre del servidor', async () => {
    const enlace = { href: '', download: '', click: vi.fn(), remove: vi.fn() }
    vi.stubGlobal('document', { createElement: () => enlace, body: { append: vi.fn() } })
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:reporte', revokeObjectURL: vi.fn() })
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response('%PDF-1.4', {
            status: 200,
            headers: { 'Content-Disposition': 'attachment; filename="cierres-de-caja_2026-11-01_2026-11-20.pdf"' },
          }),
      ),
    )

    const nombre = await descargarReporte({ codigo: 'CIERRES', filtros: FILTROS }, 'PDF')

    expect(nombre).toBe('cierres-de-caja_2026-11-01_2026-11-20.pdf')
    expect(enlace.download).toBe(nombre)
    expect(enlace.href).toBe('blob:reporte')
    expect(enlace.click).toHaveBeenCalled()
  })

  it('muestra el error del servidor cuando los filtros no son válidos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              codigo: 'FILTRO_FECHAS_INVERTIDAS',
              mensaje: 'La fecha final no puede ser anterior a la fecha inicial.',
              detalles: [],
            }),
            { status: 422 },
          ),
      ),
    )

    await expect(descargarReporte({ codigo: 'VENTAS_PERIODO', filtros: FILTROS }, 'XLSX')).rejects.toThrow(
      'La fecha final no puede ser anterior a la fecha inicial.',
    )
  })
})
