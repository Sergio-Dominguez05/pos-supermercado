import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  claveListaClientes,
  FILTRO_CLIENTES_INICIAL,
  listarClientes,
  type ClienteListado,
  type PaginaClientes,
} from '../../api/clientes'
import { CLAVE_SESION } from '../login/sesion'
import { ListaClientes } from './ListaClientes'
import { fechaUltimaCompra, resumenClientes } from './textos'

const NIT: ClienteListado = {
  id: 4,
  tipoIdentificacion: 'NIT',
  identificacion: '5769370',
  nombre: 'Comercial Ñandú',
  direccion: 'Ciudad',
  correo: 'ventas@cliente.test',
  ultimaCompra: '2026-11-19T16:30:00Z',
}
const CUI: ClienteListado = {
  ...NIT,
  id: 5,
  tipoIdentificacion: 'CUI',
  identificacion: '3012345670101',
  nombre: 'Ana López',
  correo: null,
  ultimaCompra: null,
}
const PAGINA: PaginaClientes = { clientes: [NIT, CUI], pagina: 0, tamano: 25, total: 60 }
const SUPERVISOR: Partial<UsuarioSesion> = { rol: 'SUPERVISOR', permisos: ['CLIENTE_GESTIONAR'] }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = SUPERVISOR) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveListaClientes(FILTRO_CLIENTES_INICIAL), PAGINA)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => vi.unstubAllGlobals())

describe('lista de clientes (HU-05.10)', () => {
  it('muestra identificación, datos y última compra (crit. 1)', () => {
    const html = renderizar(<ListaClientes />)

    for (const columna of ['NIT o CUI', 'Nombre', 'Dirección', 'Correo', 'Última compra']) {
      expect(html).toContain(`<th>${columna}</th>`)
    }
    expect(html).toContain('NIT 576937-0')
    expect(html).toContain('CUI 3012345670101')
    expect(html).toContain('ventas@cliente.test')
    expect(html).toContain(fechaUltimaCompra(NIT.ultimaCompra))
    expect(html).toContain('Sin compras')
  })

  it('ofrece búsqueda por NIT, CUI o nombre (crit. 2)', () => {
    expect(renderizar(<ListaClientes />)).toContain('placeholder="NIT, CUI o nombre"')
  })

  it('pagina la lista (crit. 3)', () => {
    const html = renderizar(<ListaClientes />)

    expect(html).toContain('Página 1 de 3 · 60 clientes')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Anterior/)
    expect(html).toMatch(/<button(?![^>]*disabled)[^>]*>Siguiente/)
    expect(resumenClientes(0, 25, 1)).toBe('Página 1 de 1 · 1 cliente')
  })

  it('sin permiso no muestra la tabla', () => {
    const html = renderizar(<ListaClientes />, { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] })

    expect(html).toContain('No tiene permiso para consultar los clientes.')
    expect(html).not.toContain('<table')
  })

  it('la consulta envía búsqueda y página', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(PAGINA), { status: 200 })))

    await listarClientes({ q: ' Ana López ', pagina: 2 })

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/clientes?q=Ana+L%C3%B3pez&pagina=2')
  })
})
