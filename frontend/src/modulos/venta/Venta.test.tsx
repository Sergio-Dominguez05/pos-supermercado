import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, type Turno } from '../../api/turnos'
import { CLAVE_VENTA_EN_CURSO, type Venta as DatosVenta } from '../../api/ventas'
import { puedeVender } from '../caja/turno'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { DetalleVenta, Venta } from './Venta'

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
