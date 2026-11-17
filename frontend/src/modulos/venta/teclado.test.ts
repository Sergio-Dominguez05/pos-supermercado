import { describe, expect, it } from 'vitest'
import type { LineaVenta, Venta } from '../../api/ventas'
import { ACCIONES_PENDIENTES, ATAJOS, avisoPendiente } from '../../atajos/atajos'
import { ACCIONES_DE_LA_VENTA, lineaSeleccionada, moverSeleccion, supresionEsParaLaLinea } from './teclado'

const linea = (id: number, cantidad = 1): LineaVenta => ({
  id,
  productoId: id,
  codigoBarras: `74${id}`,
  descripcion: `Producto ${id}`,
  cantidad,
  precioUnitario: 5,
  subtotalConIva: 5 * cantidad,
  baseImponible: 0,
  iva: 0,
})

const venta = (...lineas: LineaVenta[]): Venta => ({
  id: 31,
  numero: 1042,
  estado: 'EN_CURSO',
  cajaNombre: 'Caja 1',
  creadaEn: '2026-11-16T15:00:00Z',
  suspendidaEn: null,
  lineas,
  articulos: 0,
  subtotal: 0,
  iva: 0,
  total: 0,
})

/** Un campo de texto como lo ve el evento de teclado (las pruebas corren sin DOM). */
const campo = (name: string, value: string, [inicio, fin] = [value.length, value.length]) =>
  ({ tagName: 'INPUT', name, value, selectionStart: inicio, selectionEnd: fin }) as unknown as EventTarget

describe('línea seleccionada para F4 y Supr (HU-06.17)', () => {
  it('después de escanear es la línea que cambió', () => {
    const antes = venta(linea(1), linea(2))

    expect(lineaSeleccionada(antes, venta(linea(1), linea(2), linea(3)), 2)).toBe(3)
    expect(lineaSeleccionada(antes, venta(linea(1, 4), linea(2)), 2)).toBe(1)
  })

  it('si nada cambió conserva la elegida; si ya no está, la última', () => {
    const actual = venta(linea(1), linea(2), linea(3))

    expect(lineaSeleccionada(actual, venta(linea(1), linea(2), linea(3)), 1)).toBe(1)
    expect(lineaSeleccionada(actual, venta(linea(1), linea(3)), 2)).toBe(3)
    expect(lineaSeleccionada(undefined, actual, undefined)).toBe(3)
    expect(lineaSeleccionada(actual, venta(), 2)).toBeUndefined()
  })

  it('las flechas recorren las líneas sin salirse de la lista', () => {
    const actual = venta(linea(1), linea(2), linea(3))

    expect(moverSeleccion(actual, 2, -1)).toBe(1)
    expect(moverSeleccion(actual, 1, -1)).toBe(1)
    expect(moverSeleccion(actual, 3, 1)).toBe(3)
    expect(moverSeleccion(actual, undefined, 1)).toBe(3)
    expect(moverSeleccion(venta(), undefined, 1)).toBeUndefined()
  })
})

describe('Supr elimina la línea sin estorbar al escribir (HU-06.17)', () => {
  it('vale en el campo del código vacío o recién escaneado (todo seleccionado)', () => {
    expect(supresionEsParaLaLinea(campo('codigo', ''))).toBe(true)
    expect(supresionEsParaLaLinea(campo('codigo', '7401', [0, 4]))).toBe(true)
    expect(supresionEsParaLaLinea({ tagName: 'BODY' } as unknown as EventTarget)).toBe(true)
    expect(supresionEsParaLaLinea(null)).toBe(true)
  })

  it('con un código a medio escribir, en la cantidad o en la búsqueda, borra texto', () => {
    expect(supresionEsParaLaLinea(campo('codigo', '7401', [2, 2]))).toBe(false)
    expect(supresionEsParaLaLinea(campo('cantidad', '3', [0, 1]))).toBe(false)
    expect(supresionEsParaLaLinea(campo('descripcion', ''))).toBe(false)
  })
})

describe('atajos de acciones que llegan con otras historias (HU-06.17 crit. 1)', () => {
  it('cada acción del mapa la atiende la venta o espera su historia, nunca las dos', () => {
    const pendientes = Object.keys(ACCIONES_PENDIENTES)

    expect([...ACCIONES_DE_LA_VENTA, ...pendientes].sort()).toEqual(ATAJOS.map((atajo) => atajo.accion).sort())
    expect(ACCIONES_DE_LA_VENTA.filter((accion) => pendientes.includes(accion))).toEqual([])
  })

  it('cada acción pendiente es del mapa y dice con qué historia llega', () => {
    const consultar = ATAJOS.find((atajo) => atajo.accion === 'consultarPrecio')!

    expect(avisoPendiente(consultar)).toBe('Consultar precio (F3) estará disponible con HU-03.10 (#28).')
    for (const accion of Object.keys(ACCIONES_PENDIENTES)) {
      expect(ATAJOS.map((atajo) => atajo.accion)).toContain(accion)
    }
  })
})
