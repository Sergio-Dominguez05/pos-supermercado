import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { rutaExportarBitacora, type FiltrosBitacora } from '../../api/bitacora'
import { ExportarBitacora } from './Bitacora'

const FILTROS: FiltrosBitacora = {
  desde: '2026-11-20',
  hasta: '2026-11-20',
  usuarioId: '',
  cajaId: '3',
  modulo: '',
  accion: 'PRECIO_CAMBIADO',
  exitoso: 'true',
  documento: '',
}

const html = (contenido: React.ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

describe('exportar la bitácora (HU-11.09)', () => {
  it('exporta con los mismos filtros de la búsqueda, a Excel o CSV (crit. 1)', () => {
    expect(rutaExportarBitacora(FILTROS, 'CSV')).toBe(
      '/api/bitacora/exportar?formato=CSV&desde=2026-11-20&hasta=2026-11-20&cajaId=3&accion=PRECIO_CAMBIADO&exitoso=true',
    )
    const sinFiltros = { ...FILTROS, desde: '', hasta: '', cajaId: '', accion: '', exitoso: '' as const }
    expect(rutaExportarBitacora(sinFiltros, 'XLSX')).toBe('/api/bitacora/exportar?formato=XLSX')
  })

  it('ofrece Excel y CSV con la cantidad de registros filtrados', () => {
    const botones = html(<ExportarBitacora filtros={FILTROS} total={12} />)

    expect(botones).toContain('aria-label="Exportar la bitácora"')
    expect(botones).toContain('Exportar los 12 registros filtrados:')
    expect(botones).toContain('>Excel</button>')
    expect(botones).toContain('>CSV</button>')
    expect(html(<ExportarBitacora filtros={FILTROS} total={1} />)).toContain('Exportar el registro filtrado:')
  })
})
