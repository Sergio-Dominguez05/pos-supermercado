import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { ErrorDeApi } from '../../api/cliente'
import { CLAVE_CODIGOS_NO_ENCONTRADOS, esCodigoNoEncontrado, type CodigoNoEncontrado } from '../../api/productos'
import { CLAVE_SESION } from '../login/sesion'
import { AvisoNoEncontrado } from './BuscadorPorCodigo'
import { CodigosNoEncontrados } from './CodigosNoEncontrados'
import { FormularioProducto } from './NuevoProducto'
import { avisoSonoro } from './sonido'
import { avisoCodigoNoEncontrado } from './textos'

const PENDIENTE: CodigoNoEncontrado = { codigo: '7409999', veces: 3, ultimaVez: '2026-11-08T14:05:00Z', ultimaCaja: 'Caja 1' }

const renderizar = (contenido: ReactNode, ruta = '/', codigos: CodigoNoEncontrado[] = [PENDIENTE]) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, { permisos: ['PRODUCTO_EDITAR'] } satisfies Partial<UsuarioSesion>)
  cliente.setQueryData(CLAVE_CODIGOS_NO_ENCONTRADOS, codigos)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter initialEntries={[ruta]}>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('producto no encontrado (HU-03.08)', () => {
  it('el aviso es visible y dice qué código no existe (crit. 1)', () => {
    const html = renderizar(<AvisoNoEncontrado codigo=" abc-1 " onBuscarPorDescripcion={() => {}} />)

    expect(html).toContain('role="alert"')
    expect(html).toContain('class="aviso-alerta"')
    expect(html).toContain('El código ABC-1 no existe en el catálogo.')
  })

  it('el aviso ofrece buscar por descripción (crit. 2) y aclara que no se agregó nada (crit. 3)', () => {
    const html = renderizar(<AvisoNoEncontrado codigo="1" onBuscarPorDescripcion={() => {}} />)

    expect(html).toContain('Buscar por descripción</button>')
    expect(html).toContain('No se agregó ningún producto.')
  })

  it('el aviso sonoro usa Web Audio y no falla si el navegador no lo tiene (crit. 1)', () => {
    const iniciar = vi.fn()
    const nodo = () => ({ connect: vi.fn().mockReturnThis(), start: iniciar, stop: vi.fn(), type: '', frequency: {}, gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() } })
    class ContextoFalso {
      currentTime = 0
      destination = {}
      createOscillator = nodo
      createGain = nodo
      close = vi.fn().mockResolvedValue(undefined)
    }
    vi.stubGlobal('window', { AudioContext: ContextoFalso })

    avisoSonoro()
    expect(iniciar).toHaveBeenCalledTimes(2)

    vi.stubGlobal('window', {})
    expect(() => avisoSonoro()).not.toThrow()
  })

  it('reconoce el error de código inexistente que envía el servidor', () => {
    const noEncontrado = new ErrorDeApi(404, { codigo: 'PRODUCTO_NO_ENCONTRADO', mensaje: 'x', detalles: [] })
    const otro = new ErrorDeApi(500, { codigo: 'ERROR_INTERNO', mensaje: 'x', detalles: [] })

    expect(esCodigoNoEncontrado(noEncontrado)).toBe(true)
    expect(esCodigoNoEncontrado(otro)).toBe(false)
  })

  it('el administrador ve los códigos pendientes con un enlace para registrarlos (crit. 4)', () => {
    const html = renderizar(<CodigosNoEncontrados />)

    expect(html).toContain('<td>7409999</td>')
    expect(html).toContain('<td>3</td>')
    expect(html).toContain('<td>Caja 1</td>')
    expect(html).toContain('href="/catalogo/nuevo?codigo=7409999"')
  })

  it('sin códigos pendientes no muestra la sección', () => {
    expect(renderizar(<CodigosNoEncontrados />, '/', [])).toBe('')
  })

  it('el formulario de alta llega con el código ya escrito', () => {
    const html = renderizar(<FormularioProducto />, '/catalogo/nuevo?codigo=7409999')

    expect(html).toMatch(/<input[^>]*name="codigoBarras"[^>]*value="7409999"/)
  })

  it('el texto del aviso normaliza el código como el servidor', () => {
    expect(avisoCodigoNoEncontrado(' 74 01 ab ')).toBe('El código 7401AB no existe en el catálogo.')
  })
})
