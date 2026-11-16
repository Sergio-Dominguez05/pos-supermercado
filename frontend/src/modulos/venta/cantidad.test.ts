import { afterEach, describe, expect, it, vi } from 'vitest'
import { ErrorDeApi } from '../../api/cliente'
import { agregarPorCodigo, agregarProducto, cambiarCantidad } from '../../api/ventas'
import {
  CANTIDAD_NO_VALIDA,
  CANTIDAD_SIN_CONFIRMAR,
  cantidadValida,
  conConfirmacion,
  FALTA_EL_CODIGO,
  separarCantidad,
} from './cantidad'

afterEach(() => {
  vi.unstubAllGlobals()
})

const porConfirmar = () =>
  new ErrorDeApi(422, {
    codigo: 'CANTIDAD_POR_CONFIRMAR',
    mensaje: 'La cantidad 51 es mayor que 50. Confírmela para continuar.',
    detalles: [],
  })

describe('indicar la cantidad de un producto (HU-06.04)', () => {
  it('"6*" antes del código indica 6 unidades (crit. 1)', () => {
    expect(separarCantidad('6*7401234567890')).toEqual({ cantidad: 6, codigo: '7401234567890' })
    expect(separarCantidad(' 12 * 7401 ')).toEqual({ cantidad: 12, codigo: '7401' })
  })

  it('sin asterisco es el código tal cual, una unidad (HU-06.02)', () => {
    expect(separarCantidad('7401234567890')).toEqual({ codigo: '7401234567890' })
  })

  it('la cantidad debe ser un número entero mayor que cero (crit. 3)', () => {
    for (const texto of ['0*7401', '-2*7401', '2.5*7401', 'x*7401', '*7401']) {
      expect(separarCantidad(texto), texto).toEqual({ error: CANTIDAD_NO_VALIDA })
    }
    expect(cantidadValida('5')).toBe(5)
    expect(cantidadValida(' 12 ')).toBe(12)
    for (const texto of ['0', '-1', '2.5', '', 'diez']) {
      expect(cantidadValida(texto), texto).toBeUndefined()
    }
  })

  it('con la cantidad pero sin el código pide escanear el producto', () => {
    expect(separarCantidad('6*')).toEqual({ error: FALTA_EL_CODIGO })
  })

  it('envía la cantidad al servidor solo cuando se indicó', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 }))))

    await agregarPorCodigo('7401')
    await agregarPorCodigo('7401', 6)
    await agregarPorCodigo('7401', 60, true)
    await agregarProducto(5)
    await cambiarCantidad(9, 4)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas.map(([, opciones]) => opciones?.body)).toEqual([
      '{"codigo":"7401"}',
      '{"codigo":"7401","cantidad":6}',
      '{"codigo":"7401","cantidad":60,"confirmada":true}',
      '{"productoId":5}',
      '{"cantidad":4}',
    ])
    expect(llamadas[4][0]).toBe('/api/ventas/actual/lineas/9/cantidad')
    expect(llamadas[4][1]).toMatchObject({ method: 'PUT' })
  })
})

describe('confirmar una cantidad inusualmente alta (HU-06.04 crit. 4)', () => {
  it('si el servidor la pide, muestra su mensaje y reenvía confirmada', async () => {
    const enviar = vi.fn().mockRejectedValueOnce(porConfirmar()).mockResolvedValueOnce('venta')
    const confirmar = vi.fn().mockReturnValue(true)

    await expect(conConfirmacion(enviar, confirmar)).resolves.toBe('venta')
    expect(confirmar).toHaveBeenCalledWith('La cantidad 51 es mayor que 50. Confírmela para continuar.')
    expect(enviar.mock.calls).toEqual([[false], [true]])
  })

  it('si el cajero no la confirma, no se reenvía nada', async () => {
    const enviar = vi.fn().mockRejectedValueOnce(porConfirmar())

    await expect(conConfirmacion(enviar, () => false)).rejects.toThrow(CANTIDAD_SIN_CONFIRMAR)
    expect(enviar).toHaveBeenCalledTimes(1)
  })

  it('dentro del límite no pregunta nada', async () => {
    const confirmar = vi.fn()

    await expect(conConfirmacion(() => Promise.resolve('venta'), confirmar)).resolves.toBe('venta')
    expect(confirmar).not.toHaveBeenCalled()
  })

  it('los demás errores llegan tal cual, sin preguntar', async () => {
    const inactivo = new ErrorDeApi(422, { codigo: 'PRODUCTO_INACTIVO', mensaje: 'Inactivo', detalles: [] })
    const confirmar = vi.fn()

    await expect(conConfirmacion(() => Promise.reject(inactivo), confirmar)).rejects.toBe(inactivo)
    expect(confirmar).not.toHaveBeenCalled()
  })
})
