import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { exportarCatalogo, PERMISO_ARCHIVO_CATALOGO, rutaExportarCatalogo } from '../../api/archivoCatalogo'
import { ExportarCatalogo } from './ArchivoCatalogo'

const html = (contenido: React.ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('exportar el catálogo (HU-03.12)', () => {
  it('ofrece Excel y CSV y explica que se puede volver a cargar (crit. 1 y 2)', () => {
    const seccion = html(<ExportarCatalogo />)

    expect(seccion).toContain('<h2>Exportar el catálogo</h2>')
    expect(seccion).toContain('>Exportar a Excel</button>')
    expect(seccion).toContain('>Exportar a CSV</button>')
    expect(seccion).toContain('se puede corregir y volver a cargar')
    expect(PERMISO_ARCHIVO_CATALOGO).toBe('CATALOGO_IMPORTAR')
  })

  it('descarga el archivo con el nombre que da el servidor', async () => {
    expect(rutaExportarCatalogo('XLSX')).toBe('/api/productos/exportar?formato=XLSX')
    const respuesta = vi.fn(
      async () =>
        new Response('Código,Descripción,Precio', {
          status: 200,
          headers: { 'Content-Disposition': 'attachment; filename="catalogo_2026-11-20.csv"' },
        }),
    )
    vi.stubGlobal('fetch', respuesta)
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })
    const enlace = { href: '', download: '', click: vi.fn(), remove: vi.fn() }
    vi.stubGlobal('document', { createElement: () => enlace, body: { append: () => {} } })

    const nombre = await exportarCatalogo('CSV')

    expect((respuesta.mock.calls[0] as unknown[])[0]).toBe('/api/productos/exportar?formato=CSV')
    expect(nombre).toBe('catalogo_2026-11-20.csv')
    expect(enlace.click).toHaveBeenCalled()
  })
})
