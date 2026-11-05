import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_CAJA_DEL_EQUIPO, CLAVE_CAJAS, type Caja } from '../../api/cajas'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioCaja } from './FormularioCaja'
import { ListaCajas } from './ListaCajas'
import { avisoEquipoVinculado, confirmacionDesactivarCaja, confirmacionVincular } from './textos'

const CAJA_1: Caja = { id: 1, nombre: 'Caja 1', serieFel: 'A01', activo: true, equipoVinculado: true }
const CAJA_2: Caja = { id: 2, nombre: 'Caja 2', serieFel: null, activo: true, equipoVinculado: false }
const CAJA_3: Caja = { id: 3, nombre: 'Caja 3', serieFel: null, activo: false, equipoVinculado: false }

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN, equipo: Caja | null = CAJA_1) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_CAJAS, [CAJA_1, CAJA_2, CAJA_3])
  cliente.setQueryData(CLAVE_CAJA_DEL_EQUIPO, equipo)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('lista de cajas (HU-12.05)', () => {
  it('muestra nombre, serie, estado y equipo de cada caja (crit. 2)', () => {
    const html = renderizar(<ListaCajas />)

    for (const columna of ['Caja', 'Serie FEL', 'Estado', 'Equipo']) expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('<td>A01</td>')
    expect(html).toContain('Inactiva')
    expect(html).toContain('Sin vincular')
  })

  it('ofrece crear, editar, activar y desactivar (crit. 1)', () => {
    const html = renderizar(<ListaCajas />)

    expect(html).toContain('href="/configuracion/cajas/nueva"')
    expect(html).toContain('href="/configuracion/cajas/2/editar"')
    expect(html).toContain('aria-label="Desactivar Caja 2"')
    expect(html).toContain('aria-label="Activar Caja 3"')
  })

  it('dice a qué caja está vinculado este equipo y ofrece vincularlo a otra activa (crit. 4)', () => {
    const html = renderizar(<ListaCajas />)

    expect(html).toContain('Este equipo está vinculado a Caja 1.')
    expect(html).toContain('<td>Este equipo</td>')
    expect(html).toContain('aria-label="Vincular este equipo a Caja 2"')
    expect(html).not.toContain('Vincular este equipo a Caja 1')
    expect(html).not.toContain('Vincular este equipo a Caja 3')
  })

  it('avisa cuando este equipo no está vinculado', () => {
    expect(renderizar(<ListaCajas />, ADMIN, null)).toContain('Este equipo no está vinculado a ninguna caja.')
  })

  it('sin el permiso de configuración no muestra las cajas', () => {
    const html = renderizar(<ListaCajas />, { rol: 'SUPERVISOR', permisos: ['REPORTE_CONSULTAR'] })

    expect(html).toContain('No tiene permiso para modificar la configuración.')
    expect(html).not.toContain('<table')
  })
})

describe('formulario de caja (HU-12.05 crit. 2)', () => {
  it('pide nombre obligatorio y serie opcional', () => {
    const html = renderizar(<FormularioCaja />)

    expect(campo(html, 'nombre')).toContain('required')
    expect(campo(html, 'serieFel')).not.toContain('required')
    expect(html).toContain('Crear caja')
  })

  it('al editar muestra los datos actuales', () => {
    const html = renderizar(<FormularioCaja caja={CAJA_1} />)

    expect(campo(html, 'nombre')).toContain('value="Caja 1"')
    expect(campo(html, 'serieFel')).toContain('value="A01"')
    expect(html).toContain('Guardar cambios')
  })
})

describe('textos de cajas', () => {
  it('confirmar desactivar y vincular explica lo que va a pasar', () => {
    expect(confirmacionDesactivarCaja(CAJA_2)).toContain('¿Desactivar Caja 2?')
    expect(confirmacionVincular(CAJA_1, CAJA_2)).toContain('El equipo que estaba vinculado a esa caja dejará de estarlo.')
    expect(confirmacionVincular(CAJA_1, CAJA_2)).toContain('Este equipo dejará de ser Caja 2.')
    expect(confirmacionVincular(CAJA_2, null)).toBe('¿Vincular este equipo a Caja 2?')
    expect(avisoEquipoVinculado(CAJA_2)).toContain('Quien inicie sesión aquí trabajará en esa caja.')
  })
})
