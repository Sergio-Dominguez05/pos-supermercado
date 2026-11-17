import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, type Turno } from '../../api/turnos'
import {
  CLAVE_VENTA_EN_CURSO,
  CLAVE_VENTAS_SUSPENDIDAS,
  eliminarLinea,
  listarSuspendidas,
  pasarAlCobro,
  retomarVenta,
  suspenderVenta,
  type Venta as DatosVenta,
} from '../../api/ventas'
import { CLAVE_COBRO, obtenerCobro, registrarPago, type Cobro, type Pago } from '../../api/pagos'
import { ATAJOS } from '../../atajos/atajos'
import { puedeVender } from '../caja/turno'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { SuspenderVenta } from './SuspenderVenta'
import {
  avisoVentaRetomada,
  avisoVentaSuspendida,
  confirmacionCancelarVenta,
  confirmacionQuitarLinea,
  descripcionSuspendida,
  formatoDesglose,
  rotuloSuspendidas,
} from './textos'
import { descripcionPago, montoValido, soloUltimos4 } from './cobro'
import { PasoDeCobro } from './PasoDeCobro'
import { VentasSuspendidas } from './VentasSuspendidas'
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
  suspendidaEn: null,
  lineas: [],
  articulos: 0,
  subtotal: 0,
  iva: 0,
  total: 0,
}

const renderizar = (
  sesion: UsuarioSesion,
  turno: Turno | null,
  venta: DatosVenta | null = VENTA_VACIA,
  suspendidas: DatosVenta[] = [],
) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_VENTAS_SUSPENDIDAS, suspendidas)
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
    expect(html).toContain('Consultar precio<small> (llega con HU-03.10 (#28))</small>')
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

describe('retomar una venta suspendida (HU-06.13)', () => {
  const ARROZ = { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
    subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 }
  const enEspera = (id: number, numero: number, suspendidaEn: string): DatosVenta => ({
    ...VENTA_VACIA, id, numero, estado: 'SUSPENDIDA', suspendidaEn, articulos: 3, total: 15, lineas: [ARROZ],
  })
  const DOS = [enEspera(40, 1040, '2026-11-16T23:47:00Z'), enEspera(41, 1041, '2026-11-16T23:52:00Z')]
  const renderizarLista = (suspendidas: DatosVenta[]) =>
    renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <VentasSuspendidas suspendidas={suspendidas} onCerrar={() => {}} onRetomada={() => {}} />
      </QueryClientProvider>,
    ).replaceAll('<!-- -->', '')

  it('cada venta en espera muestra su número, hora, artículos y total (crit. 1)', () => {
    expect(descripcionSuspendida(DOS[0])).toBe('N.º 1040 · 17:47 · 3 artículos · Q15.00')
    expect(descripcionSuspendida({ ...DOS[0], articulos: 1, total: 5 })).toBe('N.º 1040 · 17:47 · 1 artículo · Q5.00')
  })

  it('la lista ofrece retomar cada una y el primer botón recibe el foco (crit. 2 y HU-06.17)', () => {
    const html = renderizarLista(DOS)

    expect(html).toContain('N.º 1040 · 17:47')
    expect(html).toContain('N.º 1041 · 17:52')
    expect(html.match(/>Retomar<\/button>/g)).toHaveLength(2)
    expect(html.match(/autofocus=""/gi)).toHaveLength(1)
  })

  it('sin ventas en espera lo dice', () => {
    expect(renderizarLista([])).toContain('No hay ventas en espera en esta caja.')
  })

  it('la pantalla muestra cuántas hay en espera, con su atajo F8', () => {
    expect(renderizar(CAJERO, TURNO, VENTA_VACIA, DOS)).toContain(`${rotuloSuspendidas(2)} <kbd>F8</kbd>`)
    expect(rotuloSuspendidas(2)).toBe('Ventas en espera (2)')
    expect(renderizar(CAJERO, TURNO)).not.toContain('Ventas en espera')
    expect(renderizar(CAJERO, null, null, DOS)).not.toContain('Ventas en espera')
  })

  it('avisa qué venta se retomó', () => {
    expect(avisoVentaRetomada({ ...DOS[0], estado: 'EN_CURSO' })).toBe('Venta N.º 1040 retomada: 3 artículos (Q15.00).')
  })

  it('listar es un GET y retomar un POST sobre la venta suspendida', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('[]', { status: 200 }))))

    await listarSuspendidas()
    await retomarVenta(40)
    const [[rutaLista, opcionesLista], [rutaRetomar, opcionesRetomar]] = vi.mocked(fetch).mock.calls
    expect(rutaLista).toBe('/api/ventas/suspendidas')
    expect(opcionesLista?.method).toBeUndefined()
    expect(rutaRetomar).toBe('/api/ventas/suspendidas/40/retomar')
    expect(opcionesRetomar).toMatchObject({ method: 'POST' })
    vi.unstubAllGlobals()
  })

  it('F8 ya no figura como pendiente en la ayuda', () => {
    const html = renderToString(<AyudaAtajos onCerrar={() => {}} />).replaceAll('<!-- -->', '')

    expect(html).toContain('<kbd>F8</kbd> Retomar venta</li>')
  })
})

const COBRO_SIN_PAGOS: Cobro = { ventaId: 31, numero: 1042, total: 15, pagado: 0, pendiente: 15, pagos: [] }
const PAGO: Pago = { id: 3, tipoTarjeta: 'CREDITO', autorizacion: 'A1B2C3', ultimos4: '4321', monto: 15,
  registradoEn: '2026-11-17T00:10:00Z' }

const renderizarCobro = (venta: DatosVenta, cobro: Cobro) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_COBRO, cobro)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <PasoDeCobro venta={venta} onRegresar={() => {}} />
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

describe('pasar al cobro (HU-06.15)', () => {
  const ARROZ = { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
    subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 }
  const conArroz: DatosVenta = { ...VENTA_VACIA, articulos: 3, subtotal: 13.39, iva: 1.61, total: 15, lineas: [ARROZ] }

  it('con productos ofrece cobrar con su atajo F12, antes de suspender y cancelar (crit. 1)', () => {
    const html = renderizar(CAJERO, TURNO, conArroz).replaceAll('<!-- -->', '')

    expect(html).toContain('data-control="cobrar"')
    expect(html).toContain('Cobrar <kbd>F12</kbd>')
    expect(html.indexOf('data-control="cobrar"')).toBeLessThan(html.indexOf('data-control="suspender-venta"'))
  })

  it('sin productos no se ofrece cobrar (crit. 2)', () => {
    expect(renderizar(CAJERO, TURNO)).not.toContain('data-control="cobrar"')
  })

  it('el cobro muestra el total a cobrar y permite regresar a la venta (crit. 1 y 4)', () => {
    const html = renderizarCobro(conArroz, COBRO_SIN_PAGOS)

    expect(html).toContain('<dt>Total a cobrar</dt><dd class="total">Q15.00</dd>')
    expect(html).toContain('<dd>3</dd>')
    expect(html).toContain('data-control="regresar-a-la-venta"')
    expect(html).toContain('Regresar a la venta <kbd>Esc</kbd>')
  })

  it('pasar al cobro es un POST de validación sobre la venta en curso', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(conArroz), { status: 200 })))

    await expect(pasarAlCobro()).resolves.toMatchObject({ total: 15 })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/ventas/actual/cobro')
    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({ method: 'POST' })
    vi.unstubAllGlobals()
  })

  it('F12 ya no figura como pendiente en la ayuda (HU-06.17 crit. 3)', () => {
    const html = renderToString(<AyudaAtajos onCerrar={() => {}} />).replaceAll('<!-- -->', '')

    expect(html).toContain('<kbd>F12</kbd> Cobrar</li>')
  })
})

describe('cobrar con tarjeta (HU-07.03)', () => {
  const ARROZ = { id: 1, productoId: 5, codigoBarras: '7401', descripcion: 'Arroz 1 lb', cantidad: 3, precioUnitario: 5,
    subtotalConIva: 15, baseImponible: 13.392857, iva: 1.607143 }
  const conArroz: DatosVenta = { ...VENTA_VACIA, articulos: 3, total: 15, lineas: [ARROZ] }

  it('sin pago muestra el formulario con el monto propuesto igual al pendiente (crit. 1 y 2)', () => {
    const html = renderizarCobro(conArroz, { ...COBRO_SIN_PAGOS, total: 26.2, pendiente: 26.2 })

    expect(html).toContain('aria-label="Pago con tarjeta"')
    expect(html).toMatch(/<input[^>]*name="monto"[^>]*value="26.20"/)
    expect(html).toContain('Ingrese Q26.20 en la terminal bancaria')
  })

  it('pide tipo de tarjeta y autorización, y los últimos 4 como opcionales (crit. 3 y 4)', () => {
    const html = renderizarCobro(conArroz, COBRO_SIN_PAGOS)

    const debito = html.match(/<input[^>]*value="DEBITO"[^>]*>/)?.[0] ?? ''
    expect(debito).toContain('type="radio"')
    expect(debito).toMatch(/autofocus=""/i)
    expect(html).toContain('value="CREDITO"')
    expect(html).toContain('name="autorizacion"')
    expect(html).toContain('Últimos 4 dígitos (opcional)')
    expect(html).toMatch(/<input[^>]*name="ultimos4"[^>]*maxLength="4"|<input[^>]*maxLength="4"[^>]*name="ultimos4"/i)
  })

  it('explica qué hacer si la terminal rechaza la tarjeta (crit. 6)', () => {
    expect(renderizarCobro(conArroz, COBRO_SIN_PAGOS)).toContain(
      'Si la rechaza, no registre nada: intente con otra tarjeta o regrese y cancele la venta.',
    )
  })

  it('con el pago registrado lo muestra en lugar del formulario', () => {
    const html = renderizarCobro(conArroz, { ...COBRO_SIN_PAGOS, pagado: 15, pendiente: 0, pagos: [PAGO] })

    expect(html).not.toContain('aria-label="Pago con tarjeta"')
    expect(html).toContain('Tarjeta de crédito ••••4321 · Aut. A1B2C3 · Q15.00')
    expect(html).toContain('<dt>Pendiente</dt><dd>Q0.00</dd>')
    expect(html).toContain('HU-07.08 (#63)')
  })

  it('el campo de los últimos 4 solo admite 4 dígitos y el monto, quetzales con centavos (crit. 4, 5 y 7)', () => {
    expect(soloUltimos4('4111 1111 1111 1111')).toBe('4111')
    expect(soloUltimos4('12a4')).toBe('124')
    for (const monto of ['15', '15.5', '15.50']) expect(montoValido(monto), monto).toBe(true)
    for (const monto of ['0', '-5', '10.005', 'diez', '']) expect(montoValido(monto), monto).toBe(false)
    expect(descripcionPago({ ...PAGO, tipoTarjeta: 'DEBITO', ultimos4: null })).toBe('Tarjeta de débito · Aut. A1B2C3 · Q15.00')
  })

  it('registrar es un POST con el tipo, la autorización, los últimos 4 y el monto', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 }))))

    await obtenerCobro()
    await registrarPago({ tipoTarjeta: 'DEBITO', autorizacion: '123456', monto: 15 })
    const [[rutaEstado, opcionesEstado], [rutaPago, opcionesPago]] = vi.mocked(fetch).mock.calls
    expect(rutaEstado).toBe('/api/ventas/actual/pagos')
    expect(opcionesEstado?.method).toBeUndefined()
    expect(rutaPago).toBe('/api/ventas/actual/pagos')
    expect(opcionesPago).toMatchObject({ method: 'POST', body: '{"tipoTarjeta":"DEBITO","autorizacion":"123456","monto":15}' })
    vi.unstubAllGlobals()
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
