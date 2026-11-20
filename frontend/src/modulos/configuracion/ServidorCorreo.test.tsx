import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  guardarServidorCorreo,
  probarServidorCorreo,
  PUERTO_USUAL,
  type DatosServidorCorreo,
  type ServidorCorreo,
} from '../../api/servidorCorreo'
import { CLAVE_SESION } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { FormularioServidorCorreo, PaginaServidorCorreo } from './ServidorCorreo'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }

const SIN_CONFIGURAR: ServidorCorreo = {
  configurado: false,
  servidor: null,
  puerto: null,
  seguridad: null,
  usuario: null,
  secretoGuardado: false,
  remitente: null,
  nombreRemitente: null,
  timeoutMs: null,
  modificadoEn: null,
}
const GUARDADO: ServidorCorreo = {
  configurado: true,
  servidor: 'smtp.supermercado.test',
  puerto: 587,
  seguridad: 'STARTTLS',
  usuario: 'facturas',
  secretoGuardado: true,
  remitente: 'facturas@supermercado.test',
  nombreRemitente: 'Supermercado',
  timeoutMs: 10000,
  modificadoEn: '2026-11-20T15:00:00Z',
}
const DATOS: DatosServidorCorreo = {
  servidor: 'smtp.supermercado.test',
  puerto: 587,
  seguridad: 'STARTTLS',
  usuario: 'facturas',
  secreto: '',
  remitente: 'facturas@supermercado.test',
  nombreRemitente: 'Supermercado',
  timeoutMs: 10000,
}

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

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('servidor de correo (HU-08.15)', () => {
  it('sin configurar avisa que los envíos esperan y propone STARTTLS en el puerto 587', () => {
    const html = renderizar(<FormularioServidorCorreo guardado={SIN_CONFIGURAR} />)

    expect(html).toContain('esperan y se envían cuando se configure')
    expect(campo(html, 'puerto')).toContain('value="587"')
    const starttls = html.match(/<input[^>]*value="STARTTLS"[^>]*>/)?.[0] ?? ''
    expect(starttls).toContain('checked')
    expect(PUERTO_USUAL).toEqual({ STARTTLS: 587, SSL: 465, NINGUNA: 25 })
  })

  it('nunca muestra la contraseña guardada y se puede dejar vacía para conservarla', () => {
    const html = renderizar(<FormularioServidorCorreo guardado={GUARDADO} />)

    expect(campo(html, 'servidor')).toContain('value="smtp.supermercado.test"')
    expect(campo(html, 'secreto')).toContain('type="password"')
    expect(campo(html, 'secreto')).not.toContain('required')
    expect(campo(html, 'secreto')).toContain('value=""')
    expect(html).toContain('Hay una contraseña guardada (cifrada)')
    expect(campo(html, 'remitente')).toContain('type="email"')
  })

  it('la prueba se envía al correo indicado con los datos del formulario', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })),
    )

    await probarServidorCorreo(DATOS, 'yo@supermercado.test')
    await guardarServidorCorreo(DATOS)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/configuracion/correo/probar')
    expect(JSON.parse(llamadas[0][1]?.body as string)).toEqual({
      servidor: DATOS,
      destinatario: 'yo@supermercado.test',
    })
    expect(llamadas[1][0]).toBe('/api/configuracion/correo')
    expect(llamadas[1][1]).toMatchObject({ method: 'PUT' })
  })

  it('solo quien modifica la configuración la ve, y aparece en la navegación', () => {
    expect(renderizar(<PaginaServidorCorreo />, { rol: 'SUPERVISOR', permisos: ['FACTURA_ANULAR'] })).toContain(
      'No tiene permiso para modificar la configuración.',
    )
    expect(renderizar(<NavConfiguracion actual="Correo" />)).toContain('<strong>Correo</strong>')
    expect(renderizar(<NavConfiguracion actual="Cajas" />)).toContain('href="/configuracion/correo"')
  })
})
