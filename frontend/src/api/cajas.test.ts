import { afterEach, describe, expect, it, vi } from 'vitest'
import { cajaDelEquipo, crearCaja, vincularEquipo } from './cajas'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cajas (HU-12.05)', () => {
  it('crea la caja con un POST y sus datos en el cuerpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":1}', { status: 201 })))

    await crearCaja({ nombre: 'Caja 1', serieFel: 'A01' })

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/cajas')
    expect(opciones?.method).toBe('POST')
    expect(JSON.parse(opciones?.body as string)).toEqual({ nombre: 'Caja 1', serieFel: 'A01' })
  })

  it('vincular el equipo es un POST con la cookie de sesión; el token no pasa por el frontend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":1,"equipoVinculado":true}', { status: 200 })))

    await vincularEquipo(1)

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/cajas/1/equipo')
    expect(opciones?.method).toBe('POST')
    expect(opciones?.credentials).toBe('include')
  })

  it('un equipo sin vincular (204) se lee como null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(cajaDelEquipo()).resolves.toBeNull()
  })
})
