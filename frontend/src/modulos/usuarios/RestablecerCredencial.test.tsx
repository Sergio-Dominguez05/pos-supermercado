import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { claveUsuario, claveUsuarios, FILTRO_INICIAL, type ResumenUsuario } from '../../api/usuarios'
import { CLAVE_SESION } from '../login/sesion'
import { avisoRestablecimiento } from './formato'
import { ListaUsuarios } from './ListaUsuarios'
import { FormularioRestablecer, RestablecerCredencial } from './RestablecerCredencial'

const CAJERA: ResumenUsuario = {
  id: 7, username: 'lucia.mendez', nombreCompleto: 'Lucía Méndez', rol: 'CAJERO', rolNombre: 'Cajero',
  activo: true, ultimoAcceso: null,
}
const SUPERVISOR: ResumenUsuario = { ...CAJERA, id: 8, username: 'oscar', rol: 'SUPERVISOR', rolNombre: 'Supervisor' }

const renderizar = (contenido: ReactNode, sesion?: Partial<UsuarioSesion>, ruta = '/') => {
  const cliente = new QueryClient()
  if (sesion) cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveUsuario(CAJERA.id), CAJERA)
  cliente.setQueryData(claveUsuarios(FILTRO_INICIAL), [CAJERA])
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[ruta]}>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

const ADMIN: Partial<UsuarioSesion> = {
  username: 'admin',
  rol: 'ADMIN',
  permisos: ['USUARIO_ADMINISTRAR', 'CREDENCIAL_RESTABLECER'],
}

describe('restablecer contraseña o PIN (HU-01.14)', () => {
  it('a un Cajero se le asigna un PIN temporal (crit. 1)', () => {
    const html = renderizar(<FormularioRestablecer usuario={CAJERA} />)

    expect(html).toContain('PIN temporal')
    expect(campo(html, 'credencialTemporal')).toMatch(/inputmode="numeric"/i)
    expect(html).toContain('Restablecer PIN')
  })

  it('a un Supervisor se le asigna una contraseña temporal (crit. 1)', () => {
    const html = renderizar(<FormularioRestablecer usuario={SUPERVISOR} />)

    expect(html).toContain('Contraseña temporal')
    expect(html).toContain('Asigne una contraseña temporal y entréguesela al usuario.')
    expect(campo(html, 'credencialTemporal')).toMatch(/minlength="8"/i)
  })

  it('no muestra ninguna credencial actual, solo pide la nueva (crit. 3)', () => {
    const html = renderizar(<FormularioRestablecer usuario={CAJERA} />)

    expect(html.match(/<input[^>]*type="(password|text)"/g)).toHaveLength(1)
    expect(campo(html, 'credencialTemporal')).toContain('value=""')
  })

  it('sin el permiso de restablecer no muestra el formulario (crit. 2)', () => {
    const html = renderizar(
      <Routes>
        <Route path="/usuarios/:id/credencial" element={<RestablecerCredencial />} />
      </Routes>,
      { rol: 'SUPERVISOR', permisos: ['REPORTE_CONSULTAR'] },
      '/usuarios/7/credencial',
    )

    expect(html).toContain('No tiene permiso para restablecer contraseñas ni PIN.')
    expect(html).not.toContain('<form')
  })

  it('la lista ofrece Restablecer solo a quien tiene el permiso', () => {
    const conPermiso = renderizar(<ListaUsuarios />, ADMIN)
    const sinPermiso = renderizar(<ListaUsuarios />, { ...ADMIN, permisos: ['USUARIO_ADMINISTRAR'] })

    expect(conPermiso).toContain('href="/usuarios/7/credencial"')
    expect(sinPermiso).not.toContain('/credencial')
  })

  it('el aviso pide entregar la credencial temporal según su tipo', () => {
    expect(avisoRestablecimiento('lucia', 'PIN')).toBe('Se restableció el PIN de lucia. Entréguele el PIN temporal.')
    expect(avisoRestablecimiento('oscar', 'CONTRASENA')).toContain('la contraseña temporal')
  })
})
