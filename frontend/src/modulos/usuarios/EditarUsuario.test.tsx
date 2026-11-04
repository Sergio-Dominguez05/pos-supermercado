import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { claveUsuario, type ResumenUsuario } from '../../api/usuarios'
import { CLAVE_SESION } from '../login/sesion'
import { EditarUsuario, FormularioEdicion } from './EditarUsuario'

const CAJERA: ResumenUsuario = {
  id: 7, username: 'lucia.mendez', nombreCompleto: 'Lucía Méndez', rol: 'CAJERO', rolNombre: 'Cajero',
  activo: true, ultimoAcceso: null,
}

const renderizar = (contenido: ReactNode, sesion?: Partial<UsuarioSesion>, ruta = '/') => {
  const cliente = new QueryClient()
  if (sesion) cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveUsuario(CAJERA.id), CAJERA)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[ruta]}>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Etiqueta `<input>` completa del campo, sin depender del orden de los atributos. */
const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0]

describe('edición de usuarios (HU-01.07)', () => {
  it('muestra el nombre completo y el rol actuales para cambiarlos (crit. 1)', () => {
    const html = renderizar(<FormularioEdicion usuario={CAJERA} />)

    expect(campo(html, 'nombreCompleto')).toContain('value="Lucía Méndez"')
    expect(html).toMatch(/<option value="CAJERO" selected="">Cajero<\/option>/)
  })

  it('el nombre de usuario se muestra pero no se puede modificar (crit. 2)', () => {
    const username = campo(renderizar(<FormularioEdicion usuario={CAJERA} />), 'username')

    expect(username).toContain('value="lucia.mendez"')
    expect(username).toContain('disabled')
    expect(username).toMatch(/readonly/i)
  })

  it('mientras el rol no cambie de tipo no pide credencial (crit. 3)', () => {
    expect(campo(renderizar(<FormularioEdicion usuario={CAJERA} />), 'credencialTemporal')).toBeUndefined()
  })

  it('carga el usuario de la URL y lo muestra en el formulario', () => {
    const html = renderizar(
      <Routes>
        <Route path="/usuarios/:id/editar" element={<EditarUsuario />} />
      </Routes>,
      { rol: 'ADMIN', permisos: ['USUARIO_ADMINISTRAR'] },
      '/usuarios/7/editar',
    )

    expect(campo(html, 'username')).toContain('value="lucia.mendez"')
    expect(html).toContain('href="/usuarios"')
  })

  it('sin el permiso de administrar usuarios no muestra el formulario', () => {
    const html = renderizar(
      <Routes>
        <Route path="/usuarios/:id/editar" element={<EditarUsuario />} />
      </Routes>,
      { rol: 'SUPERVISOR', permisos: ['REPORTE_CONSULTAR'] },
      '/usuarios/7/editar',
    )

    expect(html).toContain('No tiene permiso para administrar usuarios.')
    expect(html).not.toContain('<form')
  })
})
