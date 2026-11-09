import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioProducto, NuevoProducto } from './NuevoProducto'
import { avisoProductoCreado, formatoQuetzales } from './textos'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['PRODUCTO_CONSULTAR', 'PRODUCTO_EDITAR'] }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('registro de productos (HU-03.01)', () => {
  it('pide código de barras, descripción y precio, los tres obligatorios (crit. 1)', () => {
    const html = renderizar(<FormularioProducto />)

    for (const nombre of ['codigoBarras', 'descripcion', 'precio']) expect(campo(html, nombre)).toContain('required')
    expect(html).toContain('Registrar producto')
  })

  it('el código de barras recibe el foco para escanearlo directamente (crit. 3)', () => {
    const html = renderizar(<FormularioProducto />)

    expect(campo(html, 'codigoBarras')).toMatch(/autofocus/i)
    expect(html).toContain('Escanéelo con el lector o escríbalo.')
  })

  it('el precio admite como máximo 2 decimales (crit. 4)', () => {
    const regla = new RegExp(`^${campo(renderizar(<FormularioProducto />), 'precio').match(/pattern="([^"]*)"/)?.[1]}$`)

    for (const valido of ['11.20', '5', '5.1', '1,50']) expect(regla.test(valido)).toBe(true)
    for (const invalido of ['5.105', '-1', 'abc', '']) expect(regla.test(invalido)).toBe(false)
  })

  it('la descripción tiene como máximo 100 caracteres (crit. 5)', () => {
    expect(campo(renderizar(<FormularioProducto />), 'descripcion')).toContain('maxLength="100"')
  })

  it('sin el permiso de crear productos no muestra el formulario', () => {
    const html = renderizar(<NuevoProducto />, { rol: 'CAJERO', permisos: ['PRODUCTO_CONSULTAR'] })

    expect(html).toContain('No tiene permiso para crear productos.')
    expect(html).not.toContain('<form')
  })

  it('el aviso confirma el producto y que ya se puede vender (crit. 6)', () => {
    const producto = { id: 1, codigoBarras: '7401234567890', descripcion: 'Leche entera 1 L', precio: 11.2, activo: true, modificadoEn: '2026-11-08T14:05:00Z' }

    expect(avisoProductoCreado(producto)).toBe(
      'Se registró Leche entera 1 L (7401234567890) a Q11.20. Ya está disponible para la venta.',
    )
    expect(formatoQuetzales(5)).toBe('Q5.00')
  })
})
