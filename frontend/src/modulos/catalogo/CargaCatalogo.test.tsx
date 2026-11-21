import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { confirmarCarga, revisarCarga, type RevisionCarga } from '../../api/archivoCatalogo'
import { CargaCatalogo, RevisionDeCarga } from './CargaCatalogo'

const REVISION: RevisionCarga = {
  huella: 'abc123',
  filas: 5,
  nuevos: [{ fila: 2, codigo: '7401', descripcion: 'Frijol negro 1 lb', precio: 8.5 }],
  actualizados: [
    {
      fila: 3,
      productoId: 9,
      codigo: '7402',
      descripcionAntes: 'Azucar 1 lb',
      descripcionDespues: 'Azúcar 1 lb',
      precioAntes: 6,
      precioDespues: 6.25,
    },
    {
      fila: 4,
      productoId: 10,
      codigo: '7403',
      descripcionAntes: null,
      descripcionDespues: null,
      precioAntes: 3,
      precioDespues: 3.5,
    },
  ],
  sinCambios: 1,
  errores: [{ fila: 5, codigo: '', motivo: 'El código está vacío.' }],
}

const html = (contenido: React.ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cargar el catálogo desde un archivo (HU-03.05)', () => {
  it('ofrece la plantilla y pide revisar el archivo antes de cargar (crit. 1 y 5)', () => {
    const seccion = html(<CargaCatalogo />)

    expect(seccion).toContain('>Plantilla de Excel</button>')
    expect(seccion).toContain('>Plantilla CSV</button>')
    expect(seccion).toContain('Nada se guarda hasta que confirme.')
    expect(seccion).toMatch(/<button type="submit" disabled="">Revisar archivo<\/button>/)
  })

  it('la revisión separa nuevos, actualizados y errores con su motivo (crit. 2 a 4)', () => {
    const vista = html(<RevisionDeCarga revision={REVISION} confirmando={false} onConfirmar={() => {}} />)

    expect(vista).toContain('<dt>Nuevos</dt><dd>1</dd>')
    expect(vista).toContain('<dt>Se actualizarán</dt><dd>2</dd>')
    expect(vista).toContain('<dt>Sin cambios</dt><dd>1</dd>')
    expect(vista).toContain('<td>5</td><td>—</td><td>El código está vacío.</td>')
    expect(vista).toContain('<td>7401</td><td>Frijol negro 1 lb</td><td>Q8.50</td>')
    expect(vista).toContain('<td>Azucar 1 lb → Azúcar 1 lb</td><td>Q6.00 → Q6.25</td>')
    expect(vista).toContain('<td>Sin cambio</td><td>Q3.00 → Q3.50</td>')
    expect(vista).toContain('Confirmar la carga de 3 productos')
  })

  it('sin productos válidos no se puede confirmar (crit. 4 y 5)', () => {
    const vista = html(
      <RevisionDeCarga
        revision={{ ...REVISION, nuevos: [], actualizados: [] }}
        confirmando={false}
        onConfirmar={() => {}}
      />,
    )
    expect(vista).toMatch(/disabled="">No hay productos que cargar<\/button>/)
  })

  it('envía el archivo como formulario y confirma con la huella de la revisión', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(REVISION), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const archivo = new File(['Código,Descripción,Precio'], 'catalogo.csv', { type: 'text/csv' })

    await revisarCarga(archivo)
    await confirmarCarga(archivo, 'abc123')

    const llamadas = respuesta.mock.calls.map((llamada) => llamada as unknown as [string, RequestInit])
    expect(llamadas.map(([ruta]) => ruta)).toEqual(['/api/productos/carga/revision', '/api/productos/carga?huella=abc123'])
    expect(llamadas[0][1].body).toBeInstanceOf(FormData)
    expect((llamadas[0][1].body as FormData).get('archivo')).toBeInstanceOf(File)
    expect(llamadas[0][1].headers).toBeUndefined()   // el navegador pone el multipart con su límite
  })
})
