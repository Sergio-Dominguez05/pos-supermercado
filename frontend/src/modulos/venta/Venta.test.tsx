import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, type Turno } from '../../api/turnos'
import { CLAVE_VENTA_EN_CURSO, eliminarLinea, suspenderVenta, type Venta as DatosVenta } from '../../api/ventas'
import { ATAJOS } from '../../atajos/atajos'
import { puedeVender } from '../caja/turno'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { SuspenderVenta } from './SuspenderVenta'
import { avisoVentaSuspendida, confirmacionCancelarVenta, confirmacionQuitarLinea, formatoDesglose } from './textos'
import { AyudaAtajos } from './AyudaAtajos'
import { CancelarVenta, DesgloseIva, DetalleVenta, Venta } from './Venta'

const CAJERO = {
  rol: 'CAJERO',
  nombreCompleto: 'Ana López',
  rolNombre: 'Cajero',
  permisos: ['VENTA_REGISTRAR', 'CAJA_OPERAR_PROPIA'],
  cajaNombre: 'Caja 1',
} as UsuarioSesion

const TURNO: Turno = {
  id: 7,
  cajaId: 1,
  cajaNombre: 'Caja 1',
  usuarioNombre: 'Ana López',
  estado: 'ABIERTO',
  abiertoEn: '2026-11-08T14:05:00Z',
  propio: true,
}

const VENTA_VACIA: DatosVenta = {
  id: 31,
  numero: 1042,
  estado: 'EN_CURSO',
  cajaNombre: 'Caja 1',
  creadaEn: '2026-11-16T15:00:00Z',
  lineas: [],
  articulos: 0,
  subtotal: 0,
  iva: 0,
  total: 0,
}

const renderizar = (sesion: UsuarioSesion, turno: Turno | null, venta: DatosVenta | null = VENTA_VACIA) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_TURNO_ACTUAL, turno)
  if (venta) cliente.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>
        <Venta />
      </MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

const conjunto = (html: string) => html.match(/<fieldset[^>]*>/)?.[0] ?? ''

describe('impedir ventas sin caja abierta (HU-09.02)', () => {
  it('con la caja cerrada la pantalla de venta está deshabilitada (crit. 1)', () => {
    const html = renderizar(CAJERO, null)

    expect(conjunto(html)).toContain('disabled')
    expect(html).toContain('La venta está deshabilitada: abra su turno en una caja para vender.')
  })

  it('con el turno propio abierto la pantalla se habilita', () => {
    const html = renderizar(CAJERO, TURNO)

    expect(conjunto(html)).not.toContain('disabled')
  })

  it('el turno de otro usuario no habilita la venta', () => {
    expect(conjunto(renderizar(CAJERO, { ...TURNO, propio: false }))).toContain('disabled')
  })

  it('sin caja vinculada o sin permiso de venta no se puede vender', () => {
    expect(puedeVender({ ...CAJERO, cajaNombre: null }, TURNO)).toBe(false)
    expect(puedeVender({ ...CAJERO, permisos: ['CAJA_OPERAR_PROPIA'] }, TURNO)).toBe(false)
    expect(puedeVender(CAJERO, TURNO)).toBe(true)
  })
})

describe('iniciar una venta (HU-06.01)', () => {
  it('con el turno abierto la venta queda lista y muestra su número (crit. 1 y 4)', () => {
    const html = renderizar(CAJERO, TURNO)

    expect(html).toContain('Venta N.º 1042')
    expect(html).toContain('Lista para recibir productos')
    expect(conjunto(html)).not.toContain('disabled')
  })

  it('la opción Venta del menú lleva a la pantalla de venta', () => {
    expect(opcionesPara(['VENTA_REGISTRAR'])).toEqual([expect.objectContaining({ etiqueta: 'Venta', ruta: '/venta' })])
  })

  it('mientras la venta no está lista no se pueden ingresar productos', () => {
    expect(conjunto(renderizar(CAJERO, TURNO, null))).toContain('disabled')
  })

  it('con la caja cerrada no se muestra ninguna venta (crit. 2)', () => {
    const html = renderizar(CAJERO, null)

    expect(html).not.toContain('Venta N.º')
    expect(html).toContain('abra su turno')
  })

  it('muestra las líneas y los totales en orden Subtotal, IVA y TOTAL (HU-04.04 crit. 1)', () => {
    const html = renderToString(
      <DetalleVenta
        venta={{
          ...VENTA_VACIA,
          lineas: [
            {
              id: 1,
              productoId: 5,
              codigoBarras: '7401234567890',
              descripcion: 'Arroz 1 lb',
              cantidad: 3,
              precioUnitario: 5,
              subtotalConIva: 15,
              baseImponible: 13.392857,
              iva: 1.607143,
            },
          ],
          articulos: 3,
          subtotal: 13.39,
          iva: 1.61,
          total: 15,
        }}
      />,
    ).replaceAll('<!-- -->', '')

    expect(html).toContain('Arroz 1 lb')
    expect(html).toContain('Q5.00')
    const orden = ['Subtotal', 'IVA', 'TOTAL'].map((rotulo) => html.indexOf(`<dt>${rotulo}</dt>`))
    expect(orden).toEqual([...orden].sort((a, b) => a - b))
    expect(html).toContain('<dd class="total">Q15.00</dd>')
    expect(html).toContain('<dd>Q13.39</dd>')
    expect(html).toContain('<dd>Q1.61</dd>')
  })
})

describe('ver totales en tiempo real (HU-06.08)', () => {
  it('con la venta vacía ya se ven los artículos y los totales en cero (crit. 1)', () => {
    const html = renderizar(CAJERO, TURNO)

    const orden = ['Artículos', 'Subtotal', 'IVA', 'TOTAL'].map((rotulo) => html.indexOf(`<dt>${rotulo}</dt>`))
    expect(orden.every((posicion) => posicion >= 0)).toBe(true)
    expect(orden).toEqual([...orden].sort((a, b) => a - b))
    expect(html).toContain('<dd class="total">Q0.00</dd>')
  })

  it('el TOTAL se distingue con su propia clase para la letra grande (crit. 2)', () => {
    const html = renderToString(<DetalleVenta venta={{ ...VENTA_VACIA, articulos: 2, total: 26.2 }} />)

    expect(html).toContain('<dd class="total">Q26.20</dd>')
    expect(html.match(/class="total"/g)).toHaveLength(1)
  })

  it('muestra los montos tal como los redondea el servidor, sin recalcularlos (crit. 3)', () => {
    const html = renderToString(
      <DetalleVenta venta={{ ...VENTA_VACIA, articulos: 4, subtotal: 23.39, iva: 2.81, total: 26.2 }} />,
    ).replaceAll('<!-- -->', '')

    expect(html).toContain('<dd>4</dd>')
    expect(html).toContain('<dd>Q23.39</dd>')
    expect(html).toContain('<dd>Q2.81</dd>')
  })
})

describe('cambiar la cantidad de una línea (HU-06.04 crit. 2)', () => {
  const conArroz: DatosVenta = { ...VENTA_VACIA, articulos: 3, total: 15, lineas: [
    { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
      subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 },
  ] }

  it('en la pantalla de venta cada línea tiene su cantidad editable', () => {
    const html = renderizar(CAJERO, TURNO, conArroz)

    expect(html).toMatch(/<input[^>]*name="cantidad"/)
    expect(html).toContain('aria-label="Cantidad de Arroz 1 lb"')
    expect(html).toContain('value="3"')
  })

  it('el detalle de solo lectura muestra la cantidad sin campo', () => {
    expect(renderToString(<DetalleVenta venta={conArroz} />)).not.toContain('name="cantidad"')
  })

  it('el campo del código explica el multiplicador (crit. 1)', () => {
    expect(renderizar(CAJERO, TURNO)).toContain('6* antes para 6 unidades')
  })
})

describe('eliminar una línea (HU-06.07)', () => {
  const ARROZ = { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
    subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 }
  const conArroz: DatosVenta = { ...VENTA_VACIA, articulos: 3, total: 15, lineas: [ARROZ] }

  it('cada línea de la venta se puede quitar (crit. 1)', () => {
    const html = renderizar(CAJERO, TURNO, conArroz)

    expect(html).toContain('<th>Acciones</th>')
    expect(html).toMatch(/<button[^>]*aria-label="Quitar Arroz 1 lb"[^>]*>Quitar<\/button>/)
  })

  it('el detalle de solo lectura no ofrece quitar líneas', () => {
    const html = renderToString(<DetalleVenta venta={conArroz} />)

    expect(html).not.toContain('Quitar')
    expect(html).not.toContain('Acciones')
  })

  it('pide confirmación con la cantidad, el producto y el subtotal', () => {
    expect(confirmacionQuitarLinea(ARROZ)).toBe('¿Quitar 3 x Arroz 1 lb (Q15.00) de la venta?')
  })

  it('quitar es un DELETE de la línea en la venta en curso', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(VENTA_VACIA), { status: 200 })))

    await expect(eliminarLinea(9)).resolves.toMatchObject({ total: 0 })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/ventas/actual/lineas/9')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'DELETE' })
    vi.unstubAllGlobals()
  })
})

describe('operar con atajos de teclado (HU-06.17)', () => {
  const linea = (id: number) => ({ id, productoId: id, codigoBarras: `74${id}`, descripcion: `Producto ${id}`,
    cantidad: 1, precioUnitario: 5, subtotalConIva: 5, baseImponible: 4.464286, iva: 0.535714 })
  const conDos: DatosVenta = { ...VENTA_VACIA, articulos: 2, total: 10, lineas: [linea(1), linea(2)] }

  it('la pantalla indica que F1 abre los atajos (crit. 2)', () => {
    expect(renderizar(CAJERO, TURNO)).toContain('<kbd>F1</kbd> Atajos de teclado')
  })

  it('la ayuda rápida lista todo el mapa y marca lo que llega con otras historias (crit. 1 y 2)', () => {
    const html = renderToString(<AyudaAtajos onCerrar={() => {}} />).replaceAll('<!-- -->', '')

    for (const atajo of ATAJOS) expect(html).toContain(atajo.descripcion)
    expect(html).toContain('<kbd>Supr</kbd> Eliminar línea')
    expect(html).toContain('<kbd>6*</kbd> antes del código')
    expect(html).toContain('Cobrar<small> (llega con HU-06.15 (#58))</small>')
    expect(html).not.toContain('Cancelar venta<small>')
  })

  it('la última línea agregada queda seleccionada para F4 y Supr', () => {
    const html = renderizar(CAJERO, TURNO, conDos)

    expect(html).toMatch(/<tr data-linea="2" class="seleccionada" aria-selected="true">/)
    expect(html).toMatch(/<tr data-linea="1" aria-selected="false">/)
    expect(html).toContain('data-control="cantidad"')
    expect(html).toContain('data-control="quitar"')
  })

  it('el botón de cancelar muestra su atajo F10', () => {
    const html = renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <CancelarVenta venta={conDos} />
      </QueryClientProvider>,
    ).replaceAll('<!-- -->', '')

    expect(html).toContain('data-control="cancelar-venta"')
    expect(html).toContain('Cancelar venta <kbd>F10</kbd>')
  })
})

describe('suspender una venta (HU-06.12)', () => {
  const ARROZ = { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
    subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 }
  const conArroz: DatosVenta = { ...VENTA_VACIA, articulos: 3, total: 15, lineas: [ARROZ] }
  const renderizarSuspender = (venta: DatosVenta) =>
    renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <SuspenderVenta venta={venta} />
      </QueryClientProvider>,
    ).replaceAll('<!-- -->', '')

  it('con productos ofrece suspender la venta con su atajo F7 (crit. 1 y HU-06.17)', () => {
    const html = renderizarSuspender(conArroz)

    expect(html).toContain('data-control="suspender-venta"')
    expect(html).toContain('Suspender venta <kbd>F7</kbd>')
  })

  it('una venta sin productos no tiene nada que suspender', () => {
    expect(renderizarSuspender(VENTA_VACIA)).toBe('')
    expect(renderizar(CAJERO, TURNO)).not.toContain('Suspender venta')
  })

  it('la pantalla de venta muestra suspender junto a cancelar', () => {
    const html = renderizar(CAJERO, TURNO, conArroz)

    expect(html.indexOf('Suspender venta')).toBeGreaterThan(-1)
    expect(html.indexOf('Suspender venta')).toBeLessThan(html.indexOf('Cancelar venta'))
  })

  it('avisa qué venta quedó en espera (crit. 1)', () => {
    expect(avisoVentaSuspendida({ ...conArroz, numero: 1042 })).toBe(
      'Venta N.º 1042 en espera: 3 artículos (Q15.00). Ya puede atender al siguiente cliente.',
    )
    expect(avisoVentaSuspendida({ ...conArroz, articulos: 1, total: 5 })).toContain('1 artículo (Q5.00)')
  })

  it('suspender es un POST sobre la venta en curso', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(conArroz), { status: 200 })))

    await expect(suspenderVenta()).resolves.toMatchObject({ total: 15 })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/ventas/actual/suspender')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'POST' })
    vi.unstubAllGlobals()
  })

  it('F7 ya no figura como pendiente en la ayuda', () => {
    const html = renderToString(<AyudaAtajos onCerrar={() => {}} />).replaceAll('<!-- -->', '')

    expect(html).toContain('<kbd>F7</kbd> Suspender venta</li>')
  })
})

describe('IVA por línea en el detalle de la venta (HU-04.03)', () => {
  const ARROZ = {
    id: 1,
    productoId: 5,
    codigoBarras: '7401',
    descripcion: 'Arroz 1 lb',
    cantidad: 3,
    precioUnitario: 5,
    subtotalConIva: 15,
    baseImponible: 13.392857,
    iva: 1.607143,
  }

  it('el desglose de cada línea se puede consultar con 6 decimales, sin redondear a 2 (crit. 2 y 3)', () => {
    const html = renderToString(<DesgloseIva venta={{ ...VENTA_VACIA, lineas: [ARROZ] }} />).replaceAll('<!-- -->', '')

    expect(html).toContain('<summary>Desglose de IVA por línea</summary>')
    expect(html).toContain('Q15.000000')
    expect(html).toContain('Q13.392857')
    expect(html).toContain('Q1.607143')
  })

  it('el detalle de la venta incluye el desglose solo cuando hay productos', () => {
    expect(renderToString(<DetalleVenta venta={{ ...VENTA_VACIA, lineas: [ARROZ] }} />)).toContain('Desglose de IVA')
    expect(renderToString(<DetalleVenta venta={VENTA_VACIA} />)).not.toContain('Desglose de IVA')
  })

  it('formatea los montos del desglose con 6 decimales', () => {
    expect(formatoDesglose(4.464286)).toBe('Q4.464286')
    expect(formatoDesglose(0.5357140000000001)).toBe('Q0.535714')
  })
})

describe('cancelar una venta en curso (HU-06.14)', () => {
  const conProductos: DatosVenta = { ...VENTA_VACIA, articulos: 3, total: 15, lineas: [
    { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
      subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 },
  ] }
  const renderizarCancelar = (venta: DatosVenta) =>
    renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <CancelarVenta venta={venta} />
      </QueryClientProvider>,
    )

  it('con productos ofrece cancelar la venta', () => {
    expect(renderizarCancelar(conProductos)).toContain('Cancelar venta')
  })

  it('una venta sin productos no tiene nada que cancelar', () => {
    expect(renderizarCancelar(VENTA_VACIA)).toBe('')
  })

  it('pide confirmación con el número, los artículos y el total (crit. 1)', () => {
    expect(confirmacionCancelarVenta(conProductos)).toBe(
      '¿Cancelar la venta N.º 1042? Se quitarán 3 artículos (Q15.00) y no se podrá recuperar.',
    )
    expect(confirmacionCancelarVenta({ ...conProductos, articulos: 1, total: 5 })).toContain('1 artículo (Q5.00)')
  })
})
