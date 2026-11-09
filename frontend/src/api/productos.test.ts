import { afterEach, describe, expect, it, vi } from 'vitest'
import { crearProducto } from './productos'

afterEach(() => {
  vi.unstubAllGlobals()
})

const cuerpoEnviado = () => JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)

describe('productos (HU-03.01)', () => {
  it('registra el producto con un POST y sus datos en el cuerpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":1}', { status: 201 })))

    await crearProducto({ codigoBarras: '7401234567890', descripcion: 'Leche entera 1 L', precio: '11.20' })

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/productos')
    expect(opciones?.method).toBe('POST')
    expect(cuerpoEnviado()).toEqual({ codigoBarras: '7401234567890', descripcion: 'Leche entera 1 L', precio: 11.2 })
  })

  it('acepta la coma como separador decimal', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":1}', { status: 201 })))

    await crearProducto({ codigoBarras: '1', descripcion: 'Pan', precio: '1,50' })

    expect(cuerpoEnviado().precio).toBe(1.5)
  })

  it('un precio vacío viaja como null para que el servidor lo rechace', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":1}', { status: 201 })))

    await crearProducto({ codigoBarras: '1', descripcion: 'Pan', precio: '  ' })

    expect(cuerpoEnviado().precio).toBeNull()
  })

  it('el código repetido llega como error con el mensaje del servidor (crit. 2)', async () => {
    const error = { codigo: 'CODIGO_DUPLICADO', mensaje: "El código de barras ya está asignado al producto 'Pan'.", detalles: [] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(error), { status: 422 })))

    await expect(crearProducto({ codigoBarras: '1', descripcion: 'Otro', precio: '1' })).rejects.toMatchObject({
      estado: 422,
      codigo: 'CODIGO_DUPLICADO',
      message: "El código de barras ya está asignado al producto 'Pan'.",
    })
  })
})
