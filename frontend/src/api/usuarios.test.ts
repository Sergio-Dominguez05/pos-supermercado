import { afterEach, describe, expect, it, vi } from 'vitest'
import { crearUsuario } from './usuarios'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('usuarios', () => {
  it('crea el usuario con un POST y la credencial en el cuerpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 7 }), { status: 201 })))

    await crearUsuario({ nombreCompleto: 'Ana', username: 'ana', rol: 'CAJERO', credencialTemporal: '4821' })

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/usuarios')
    expect(opciones?.method).toBe('POST')
    expect(JSON.parse(opciones?.body as string)).toEqual({
      nombreCompleto: 'Ana',
      username: 'ana',
      rol: 'CAJERO',
      credencialTemporal: '4821',
    })
  })

  it('muestra el mensaje del servidor cuando el usuario ya existe (crit. 4)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ codigo: 'USERNAME_DUPLICADO', mensaje: 'El nombre de usuario ya existe.', detalles: [] }),
      { status: 422 },
    )))

    await expect(
      crearUsuario({ nombreCompleto: 'Ana', username: 'ana', rol: 'CAJERO', credencialTemporal: '4821' }),
    ).rejects.toThrow('El nombre de usuario ya existe.')
  })
})
