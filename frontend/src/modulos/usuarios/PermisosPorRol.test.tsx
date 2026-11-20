import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { guardarPermisosDeRol, type MatrizPermisos } from '../../api/permisos'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { asignacionDe, rolesCambiados } from './permisos'
import { EditorPermisos, PaginaPermisos } from './PermisosPorRol'

const MATRIZ: MatrizPermisos = {
  modulos: [
    {
      codigo: 'catalogo',
      nombre: 'Catálogo de productos',
      permisos: [
        {
          codigo: 'PRODUCTO_CONSULTAR',
          descripcion: 'Consultar el catálogo de productos',
          exclusivoAdmin: false,
          supervisor: true,
          cajero: true,
        },
        {
          codigo: 'PRODUCTO_EDITAR',
          descripcion: 'Crear productos y cambiar precios',
          exclusivoAdmin: true,
          supervisor: false,
          cajero: false,
        },
      ],
    },
    {
      codigo: 'reportes',
      nombre: 'Reportes',
      permisos: [
        {
          codigo: 'REPORTE_CONSULTAR',
          descripcion: 'Consultar reportes',
          exclusivoAdmin: false,
          supervisor: true,
          cajero: false,
        },
      ],
    },
  ],
  rolesConfigurables: ['SUPERVISOR', 'CAJERO'],
}

const renderizar = (contenido: ReactNode, permisos: string[]) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, { rol: 'ADMIN', permisos } satisfies Partial<UsuarioSesion>)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const casilla = (html: string, etiqueta: string) =>
  html.match(new RegExp(`<input[^>]*aria-label="${etiqueta}"[^>]*>`))?.[0] ?? ''

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('permisos por rol (HU-01.10)', () => {
  it('agrupa los permisos por módulo con una columna por rol (crit. 1 y 2)', () => {
    const html = renderizar(<EditorPermisos matriz={MATRIZ} />, ['PERMISO_CONFIGURAR'])

    expect(html).toContain('<h2>Catálogo de productos</h2>')
    expect(html).toContain('<h2>Reportes</h2>')
    for (const columna of ['Permiso', 'Administrador', 'Supervisor', 'Cajero'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(casilla(html, 'Consultar reportes: Supervisor')).toContain('checked')
    expect(casilla(html, 'Consultar reportes: Cajero')).not.toContain('checked')
    expect(casilla(html, 'Consultar reportes: Cajero')).not.toContain('disabled')
  })

  it('el Administrador los tiene todos y los exclusivos no se pueden asignar (crit. 3 y 4)', () => {
    const html = renderizar(<EditorPermisos matriz={MATRIZ} />, ['PERMISO_CONFIGURAR'])

    expect(casilla(html, 'Consultar reportes: Administrador')).toMatch(/checked.*disabled|disabled.*checked/)
    expect(casilla(html, 'Crear productos y cambiar precios: Supervisor')).toContain('disabled')
    expect(casilla(html, 'Crear productos y cambiar precios: Cajero')).toContain('disabled')
    expect(html).toContain('Crear productos y cambiar precios<small class="pendiente"> · Solo Administrador</small>')
    expect(html).toContain('desde el siguiente inicio de sesión de cada usuario')
  })

  it('solo guarda los roles que cambiaron', async () => {
    const guardada = asignacionDe(MATRIZ)
    expect(rolesCambiados(guardada, asignacionDe(MATRIZ))).toEqual([])
    const editada = { ...guardada, CAJERO: new Set([...guardada.CAJERO, 'REPORTE_CONSULTAR']) }
    expect(rolesCambiados(guardada, editada)).toEqual(['CAJERO'])

    const respuesta = vi.fn(async () => new Response(JSON.stringify(MATRIZ), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    await guardarPermisosDeRol('CAJERO', ['PRODUCTO_CONSULTAR', 'REPORTE_CONSULTAR'])
    expect(respuesta).toHaveBeenCalledWith(
      '/api/roles/CAJERO/permisos',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ permisos: ['PRODUCTO_CONSULTAR', 'REPORTE_CONSULTAR'] }),
      }),
    )
  })

  it('solo quien configura permisos ve la pantalla y la opción del menú', () => {
    expect(renderizar(<PaginaPermisos />, ['USUARIO_ADMINISTRAR'])).toContain(
      'No tiene permiso para configurar los permisos por rol.',
    )
    expect(opcionesPara(['PERMISO_CONFIGURAR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Permisos por rol', ruta: '/permisos' }),
    ])
  })
})
