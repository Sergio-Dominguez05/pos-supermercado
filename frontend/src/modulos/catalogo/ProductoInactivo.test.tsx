import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { Producto } from '../../api/productos'
import { ResultadoEscaneo } from './BuscadorPorCodigo'
import { avisoProductoInactivo } from './textos'

const GALLETA: Producto = {
  id: 9,
  codigoBarras: '7402',
  descripcion: 'Galleta descontinuada',
  precio: 3.5,
  activo: false,
  modificadoEn: '2026-11-08T14:05:00Z',
}

const renderizar = (contenido: ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

describe('excluir productos inactivos de la venta (HU-03.09)', () => {
  it('al escanear un producto inactivo se indica que no está disponible para la venta (crit. 2)', () => {
    const html = renderizar(<ResultadoEscaneo producto={GALLETA} />)

    expect(html).toContain('role="alert"')
    expect(html).toContain('Galleta descontinuada (7402) no está disponible para la venta.')
  })

  it('un producto activo no muestra ese aviso', () => {
    expect(renderizar(<ResultadoEscaneo producto={{ ...GALLETA, activo: true }} />)).not.toContain('no está disponible')
  })

  it('el mensaje coincide con el que da el servidor al intentar venderlo', () => {
    expect(avisoProductoInactivo(GALLETA)).toBe('Galleta descontinuada (7402) no está disponible para la venta.')
  })
})
