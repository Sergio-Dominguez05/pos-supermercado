import { afterEach, describe, expect, it, vi } from 'vitest'
import { iniciarSesion, iniciarSesionConPin, obtenerSesion } from './autenticacion'

const responder = (estado: number, cuerpo?: unknown) =>
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado }),
  ))

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('autenticación', () => {
  it('sin sesión (401) devuelve null en lugar de fallar', async () => {
    responder(401)
    await expect(obtenerSesion()).resolves.toBeNull()
  })

  it('muestra el mensaje genérico del servidor cuando las credenciales no son válidas', async () => {
    responder(401, { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Usuario o contraseña incorrectos', detalles: [] })
    await expect(iniciarSesion('ana', 'mala')).rejects.toThrow('Usuario o contraseña incorrectos')
  })

  it('envía las credenciales en el cuerpo, nunca en la URL', async () => {
    responder(200, { username: 'ana' })
    await iniciarSesion('ana', 'Clave2026')

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/auth/login')
    expect(opciones?.method).toBe('POST')
    expect(JSON.parse(opciones?.body as string)).toEqual({ username: 'ana', contrasena: 'Clave2026' })
  })

  it('el PIN va a su propio endpoint, también en el cuerpo', async () => {
    responder(200, { username: 'luis' })
    await iniciarSesionConPin('luis', '4821')

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/auth/login-pin')
    expect(opciones?.method).toBe('POST')
    expect(JSON.parse(opciones?.body as string)).toEqual({ username: 'luis', pin: '4821' })
  })
})
