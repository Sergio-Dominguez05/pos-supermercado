import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { claveUsuarios, FILTRO_INICIAL, type ResumenUsuario, type UsuarioCreado } from '../../api/usuarios'
import { CLAVE_SESION } from '../login/sesion'
import { avisoCreacion, avisoEdicion, formatearUltimoAcceso, nombreDelRol, requiereCredencialNueva } from './formato'
import { ListaUsuarios } from './ListaUsuarios'

const USUARIOS: ResumenUsuario[] = [
  {
    id: 1, username: 'oscar.nunez', nombreCompleto: 'Óscar Núñez', rol: 'SUPERVISOR', rolNombre: 'Supervisor',
    activo: true, ultimoAcceso: '2026-11-01T16:30:00Z',
  },
  {
    id: 2, username: 'paula.q', nombreCompleto: 'Paula Quintero', rol: 'CAJERO', rolNombre: 'Cajero',
    activo: false, ultimoAcceso: null,
  },
]

const renderizar = (
  sesion: Partial<UsuarioSesion>,
  usuarios: ResumenUsuario[] = USUARIOS,
  aviso?: string,
) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(claveUsuarios(FILTRO_INICIAL), usuarios)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[{ pathname: '/usuarios', state: aviso ? { aviso } : null }]}>
        <ListaUsuarios />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['USUARIO_ADMINISTRAR'] }

describe('lista de usuarios (HU-01.06)', () => {
  it('muestra nombre completo, usuario, rol, estado y último acceso (crit. 1)', () => {
    const html = renderizar(ADMIN)

    for (const columna of ['Nombre completo', 'Usuario', 'Rol', 'Estado', 'Último acceso']) {
      expect(html).toContain(`<th>${columna}</th>`)
    }
    expect(html).toContain('Óscar Núñez')
    expect(html).toContain('oscar.nunez')
    expect(html).toContain('Supervisor')
    expect(html).toContain('Activo')
    expect(html).toContain('Inactivo')
    expect(html).toContain('Nunca')
  })

  it('ofrece búsqueda y filtros por rol y estado (crit. 2 y 3)', () => {
    const html = renderizar(ADMIN)

    expect(html).toMatch(/<input[^>]*type="search"[^>]*name="q"/)
    expect(html).toMatch(/<select[^>]*name="rol"/)
    expect(html).toMatch(/<select[^>]*name="activo"/)
    expect(html).toContain('>Inactivos</option>')
  })

  it('lleva a crear un usuario nuevo', () => {
    expect(renderizar(ADMIN)).toContain('href="/usuarios/nuevo"')
  })

  it('avisa cuando ningún usuario coincide', () => {
    expect(renderizar(ADMIN, [])).toContain('No hay usuarios que coincidan.')
  })

  it('al volver de crear un usuario muestra la confirmación junto a la lista', () => {
    const creado: UsuarioCreado = {
      id: 9, username: 'caja.prueba', nombreCompleto: 'Prueba Cajero', rol: 'CAJERO',
      activo: true, credencialTemporal: true,
    }
    const html = renderizar(ADMIN, USUARIOS, avisoCreacion(creado))

    expect(html).toContain('role="status">Se creó el usuario caja.prueba (Cajero), activo y con PIN temporal.')
    expect(html).toContain('<table')
  })

  it('al volver de editar un usuario muestra la confirmación (HU-01.07)', () => {
    const html = renderizar(ADMIN, USUARIOS, avisoEdicion(USUARIOS[0], true))

    expect(html).toContain(
      'Se actualizó el usuario oscar.nunez. El nuevo rol se aplica a partir de su siguiente inicio de sesión.',
    )
  })

  it('cada fila lleva a editar su usuario (HU-01.07)', () => {
    const html = renderizar(ADMIN)

    expect(html).toContain('href="/usuarios/1/editar"')
    expect(html).toContain('href="/usuarios/2/editar"')
  })

  it('sin venir de crear un usuario no muestra la confirmación', () => {
    expect(renderizar(ADMIN)).not.toContain('Se creó el usuario')
  })

  it('sin el permiso de administrar usuarios no muestra la lista', () => {
    const html = renderizar({ rol: 'SUPERVISOR', permisos: ['REPORTE_CONSULTAR'] })

    expect(html).toContain('No tiene permiso para administrar usuarios.')
    expect(html).not.toContain('<table')
  })
})

describe('nombre del rol', () => {
  it('traduce el código del rol a su nombre', () => {
    expect(nombreDelRol('ADMIN')).toBe('Administrador')
    expect(nombreDelRol('SUPERVISOR')).toBe('Supervisor')
    expect(nombreDelRol('CAJERO')).toBe('Cajero')
  })
})

describe('credencial al cambiar de rol (HU-01.07 crit. 3)', () => {
  it('se pide solo al pasar entre Cajero y Administrador o Supervisor', () => {
    expect(requiereCredencialNueva('CAJERO', 'SUPERVISOR')).toBe(true)
    expect(requiereCredencialNueva('ADMIN', 'CAJERO')).toBe(true)
    expect(requiereCredencialNueva('SUPERVISOR', 'ADMIN')).toBe(false)
    expect(requiereCredencialNueva('CAJERO', 'CAJERO')).toBe(false)
  })
})

describe('último acceso', () => {
  it('se muestra en la hora de Guatemala, o "Nunca"', () => {
    expect(formatearUltimoAcceso(null)).toBe('Nunca')
    // 16:30 UTC son las 10:30 en Guatemala (UTC-6), sin importar la zona del navegador.
    expect(formatearUltimoAcceso('2026-11-01T16:30:00Z')).toMatch(/10:30/)
  })
})
