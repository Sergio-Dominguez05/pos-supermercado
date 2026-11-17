import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  CLAVE_CONEXION_FEL,
  guardarConexionFel,
  probarConexionFel,
  type ConexionFel,
  type DatosConexionFel,
} from '../../api/conexionFel'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioConexionFel, PaginaConexionFel } from './ConexionFel'
import { avisoConexionGuardada, CONFIRMACION_PRODUCCION, segundosAMs, segundosValidos } from './textos'

const ADMIN: Partial<UsuarioSesion> = { rol: 'ADMIN', permisos: ['CONFIGURACION_MODIFICAR'] }
const SUPERVISOR: Partial<UsuarioSesion> = { rol: 'SUPERVISOR', permisos: ['FACTURA_ANULAR'] }

const SIN_CONFIGURAR: ConexionFel = { configurada: false, certificador: null, urlServicio: null, usuario: null,
  secretoGuardado: false, ambiente: null, timeoutMs: null, modificadoEn: null }
const GUARDADA: ConexionFel = { configurada: true, certificador: 'Certificador S.A.', urlServicio: 'https://fel.test/api',
  usuario: 'usuario-fel', secretoGuardado: true, ambiente: 'PRUEBAS', timeoutMs: 8000,
  modificadoEn: '2026-11-17T03:00:00Z' }
const DATOS: DatosConexionFel = { certificador: 'Certificador S.A.', urlServicio: 'https://fel.test/api',
  usuario: 'usuario-fel', secreto: 'llave', ambiente: 'PRUEBAS', timeoutMs: 8000 }

const renderizar = (contenido: ReactNode, sesion: Partial<UsuarioSesion> = ADMIN, conexion = GUARDADA) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_CONEXION_FEL, conexion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''

describe('conexión con el certificador FEL (HU-08.01, HU-12.03 y HU-12.15)', () => {
  it('pide certificador, dirección, usuario, llave, ambiente y tiempo límite (HU-12.03 crit. 1 y 4)', () => {
    const html = renderizar(<FormularioConexionFel guardada={SIN_CONFIGURAR} />)

    for (const nombre of ['certificador', 'urlServicio', 'usuario', 'secreto', 'segundos'])
      expect(campo(html, nombre), nombre).not.toBe('')
    expect(html).toContain('value="PRUEBAS"')
    expect(html).toContain('value="PRODUCCION"')
    expect(campo(html, 'segundos')).toContain('value="8"')
    expect(html).toContain('Si el certificador no responde en este tiempo, la factura pasa a contingencia.')
  })

  it('la dirección debe ser https (HU-08.01 crit. 2)', () => {
    const url = campo(renderizar(<FormularioConexionFel guardada={SIN_CONFIGURAR} />), 'urlServicio')

    expect(url).toContain('pattern="https://.+"')
    expect(url).toContain('value="https://"')
  })

  it('la llave guardada no se muestra y se puede conservar (HU-12.15 crit. 2)', () => {
    const html = renderizar(<FormularioConexionFel guardada={GUARDADA} />)
    const secreto = campo(html, 'secreto')

    expect(secreto).toContain('type="password"')
    expect(secreto).toContain('value=""')
    expect(secreto).toContain('placeholder="••••••••"')
    expect(secreto).not.toContain('required')
    expect(html).toContain('Déjelo vacío para conservarla.')
    // Sin llave guardada es obligatoria.
    expect(campo(renderizar(<FormularioConexionFel guardada={SIN_CONFIGURAR} />), 'secreto')).toContain('required')
  })

  it('se puede probar la conexión antes de guardar (HU-12.03 crit. 2)', () => {
    const html = renderizar(<FormularioConexionFel guardada={GUARDADA} />)

    expect(html).toContain('data-control="probar-conexion"')
    expect(html).toContain('>Probar conexión</button>')
    expect(html.indexOf('probar-conexion')).toBeLessThan(html.indexOf('>Guardar</button>'))
  })

  it('pasar a producción se confirma explícitamente (HU-12.03 crit. 3)', () => {
    expect(CONFIRMACION_PRODUCCION).toContain('PRODUCCIÓN')
    expect(CONFIRMACION_PRODUCCION).toContain('validez fiscal')
  })

  it('solo quien modifica la configuración ve la pantalla (HU-12.15 crit. 3)', () => {
    expect(renderizar(<PaginaConexionFel />, SUPERVISOR)).toContain('No tiene permiso para modificar la configuración.')
    expect(renderizar(<PaginaConexionFel />)).toContain('aria-label="Conexión con el certificador FEL"')
  })

  it('el tiempo límite se escribe en segundos, de 1 a 60', () => {
    expect(segundosAMs('8')).toBe(8000)
    for (const valido of ['1', '8', '60']) expect(segundosValidos(valido), valido).toBe(true)
    for (const invalido of ['0', '61', '2.5', '', 'ocho']) expect(segundosValidos(invalido), invalido).toBe(false)
    expect(avisoConexionGuardada('PRODUCCION')).toBe('Se guardó la conexión con el certificador, ambiente de producción.')
  })

  it('guardar es un PUT y probar un POST, con los datos del formulario', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 }))))

    await guardarConexionFel({ ...DATOS, confirmarProduccion: true })
    await probarConexionFel(DATOS)
    const [[rutaGuardar, opcionesGuardar], [rutaProbar, opcionesProbar]] = vi.mocked(fetch).mock.calls
    expect(rutaGuardar).toBe('/api/configuracion/fel')
    expect(opcionesGuardar).toMatchObject({ method: 'PUT' })
    expect(JSON.parse(String(opcionesGuardar?.body))).toMatchObject({ confirmarProduccion: true, timeoutMs: 8000 })
    expect(rutaProbar).toBe('/api/configuracion/fel/probar')
    expect(opcionesProbar).toMatchObject({ method: 'POST' })
    vi.unstubAllGlobals()
  })
})
