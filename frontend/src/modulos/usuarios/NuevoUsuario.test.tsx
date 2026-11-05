import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_SESION } from '../login/sesion'
import { normalizarUsername } from './formato'
import { FormularioUsuario, NuevoUsuario } from './NuevoUsuario'

const renderizar = (contenido: ReactNode, sesion?: Partial<UsuarioSesion>) => {
  const cliente = new QueryClient()
  if (sesion) cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Etiqueta `<input>` completa del campo, sin depender del orden de los atributos. */
const campo = (html: string, nombre: string) => {
  const etiqueta = html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))
  if (!etiqueta) throw new Error(`No existe el campo ${nombre}`)
  return etiqueta[0]
}

describe('creación de usuarios (HU-01.05)', () => {
  it('pide nombre completo, nombre de usuario y rol (crit. 1)', () => {
    const html = renderizar(<FormularioUsuario />)

    expect(campo(html, 'nombreCompleto')).toContain('required')
    expect(campo(html, 'username')).toContain('required')
    expect(html).toMatch(/<select[^>]*name="rol"/)
    expect(html).toContain('>Administrador</option>')
    expect(html).toContain('>Supervisor</option>')
    expect(html).toContain('>Cajero</option>')
  })

  it('para Administrador o Supervisor pide una contraseña temporal de al menos 8 caracteres (crit. 2)', () => {
    const contrasena = campo(renderizar(<FormularioUsuario rolInicial="SUPERVISOR" />), 'credencialTemporal')

    expect(contrasena).toContain('type="password"')
    expect(contrasena).toMatch(/minlength="8"/i)
    expect(contrasena).not.toMatch(/inputmode/i)
  })

  it('para Cajero pide un PIN temporal numérico de 4 a 6 dígitos (crit. 3)', () => {
    const pin = campo(renderizar(<FormularioUsuario rolInicial="CAJERO" />), 'credencialTemporal')

    expect(pin).toContain('type="password"')
    expect(pin).toMatch(/inputmode="numeric"/i)
    expect(pin).toContain('pattern="\\d{4,6}"')
    expect(pin).toMatch(/maxlength="6"/i)
  })

  it('sin el permiso de administrar usuarios no muestra el formulario', () => {
    const supervisor = renderizar(<NuevoUsuario />, { rol: 'SUPERVISOR', permisos: ['REPORTE_CONSULTAR'] })
    const admin = renderizar(<NuevoUsuario />, { rol: 'ADMIN', permisos: ['USUARIO_ADMINISTRAR'] })

    expect(supervisor).toContain('No tiene permiso para administrar usuarios.')
    expect(supervisor).not.toContain('<form')
    expect(admin).toContain('<form')
  })
})

describe('espacios en el nombre de usuario (#136)', () => {
  it('se quitan todos, como en el servidor', () => {
    expect(normalizarUsername(' Juan  Perez ')).toBe('juanperez')
    expect(normalizarUsername(' A d   Mi  N')).toBe('admin')
    expect(normalizarUsername('ana	.lopez ')).toBe('ana.lopez')
  })

  it('el campo admite escribirlos y avisa que se guarda sin espacios', () => {
    const html = renderizar(<FormularioUsuario />)

    expect(campo(html, 'username')).toContain(String.raw`pattern="[A-Za-z0-9._\- ]+"`)
    expect(html).toContain('Se guarda en minúsculas y sin espacios')
  })
})
