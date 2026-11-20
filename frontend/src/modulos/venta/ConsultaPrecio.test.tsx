import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ACCIONES_PENDIENTES, ATAJOS } from '../../atajos/atajos'
import { ConsultaPrecio } from './ConsultaPrecio'
import { ACCIONES_DE_LA_VENTA } from './teclado'

const renderizar = () =>
  renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <ConsultaPrecio onCerrar={() => {}} />
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')

describe('consultar precio sin vender (HU-03.10)', () => {
  it('F3 abre la consulta por código o por descripción (crit. 1)', () => {
    expect(ATAJOS.find((atajo) => atajo.tecla === 'F3')?.accion).toBe('consultarPrecio')
    expect(ACCIONES_DE_LA_VENTA).toContain('consultarPrecio')
    expect(ACCIONES_PENDIENTES.consultarPrecio).toBeUndefined()

    const html = renderizar()
    expect(html).toContain('aria-label="Consultar precio"')
    expect(html).toContain('aria-label="Buscar por código"')
    expect(html).toMatch(/<input[^>]*name="codigo"[^>]*autofocus|<input[^>]*autofocus[^>]*name="codigo"/i)
    expect(html).toContain('name="descripcion"')
  })

  it('avisa que no se agrega a la venta y cómo volver con una tecla (crit. 2 y 3)', () => {
    const html = renderizar()
    expect(html).toContain('Lo que consulte aquí no se agrega a la venta.')
    expect(html).toContain('<kbd>Esc</kbd> o <kbd>F3</kbd> para volver a la venta.')
    expect(html).toContain('>Volver a la venta</button>')
  })
})
