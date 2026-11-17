import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  asignarClienteAVenta,
  buscarClientePorNit,
  claveClienteDeVenta,
  registrarCliente,
  type BusquedaNit,
  type Cliente,
  type ClienteDeVenta,
} from '../../api/clientes'
import { ATAJOS, ACCIONES_PENDIENTES } from '../../atajos/atajos'
import { ClienteDeLaVenta, RegistroCliente } from './ClienteDeLaVenta'
import { avisoBusqueda, CONSUMIDOR_FINAL, describirCliente, nitConGuion } from './textos'

const FERRETERIA: Cliente = { id: 5, nit: '5769370', nombre: 'Ferretería El Clavo', direccion: 'Ciudad', correo: null }

const renderizar = (contenido: ReactNode, deLaVenta: ClienteDeVenta) => {
  const cliente = new QueryClient()
  cliente.setQueryData(claveClienteDeVenta(31), deLaVenta)
  return renderToString(<QueryClientProvider client={cliente}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )
}

const campo = (html: string, nombre: string) => html.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''
const sinAccion = () => undefined
const busqueda = (origen: BusquedaNit['origen'], cliente: Cliente | null, mensaje: string | null = null): BusquedaNit =>
  ({ origen, nit: '5769370', cliente, mensaje })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cliente de la venta (EP-05)', () => {
  it('cada venta empieza a Consumidor Final y vuelve a CF con un clic (HU-05.01 crit. 1 y 2)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, { consumidorFinal: true, cliente: null })

    expect(html).toContain(`<strong>${CONSUMIDOR_FINAL}</strong>`)
    expect(html).toContain('data-control="consumidor-final"')
    expect(html).toContain('<kbd>F6</kbd>')
    expect(campo(html, 'nit')).not.toBe('')
  })

  it('muestra el NIT, el nombre y la dirección del cliente asignado (HU-05.02 crit. 1)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, { consumidorFinal: false, cliente: FERRETERIA })

    expect(html).toContain('NIT 576937-0 · Ferretería El Clavo · Ciudad')
    expect(describirCliente(null)).toBe('CF · Consumidor Final')
    expect(nitConGuion('576937K')).toBe('576937-K')
  })

  it('dice de dónde salió el cliente o por qué hay que escribir el nombre (HU-05.02 y HU-05.04)', () => {
    expect(avisoBusqueda(busqueda('REGISTRADO', FERRETERIA))).toBe('Cliente cargado: Ferretería El Clavo.')
    expect(avisoBusqueda(busqueda('CONSULTADO', FERRETERIA))).toBe(
      'Nombre obtenido del certificador: Ferretería El Clavo. Se guardó para las próximas compras.',
    )
    expect(avisoBusqueda(busqueda('SIN_CONSULTA', null, 'No se pudo consultar el NIT en este momento.'))).toBe(
      'No se pudo consultar el NIT en este momento.',
    )
  })

  it('el registro pide el nombre; la dirección y el correo son opcionales (HU-05.05 crit. 1)', () => {
    const html = renderizar(
      <RegistroCliente nit="5769370" onRegistrado={sinAccion} onConsumidorFinal={sinAccion} />,
      { consumidorFinal: true, cliente: null },
    )

    expect(campo(html, 'nombreCliente')).toContain('required')
    expect(campo(html, 'direccionCliente')).not.toContain('required')
    expect(campo(html, 'direccionCliente')).toContain('placeholder="Ciudad"')
    expect(campo(html, 'correoCliente')).toContain('type="email"')
    expect(html).toContain('Registrar y usar')
    expect(html).toContain('Facturar como CF')
  })

  it('busca, registra y asigna con las rutas del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await buscarClientePorNit(' 576937-0 ')
    await registrarCliente({ nit: '5769370', nombre: 'Ana', direccion: '', correo: '' })
    await asignarClienteAVenta(null)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/clientes/nit/576937-0')
    expect(llamadas[1][0]).toBe('/api/clientes')
    expect(llamadas[1][1]).toMatchObject({ method: 'POST' })
    expect(llamadas[2][0]).toBe('/api/ventas/actual/cliente')
    expect(JSON.parse(llamadas[2][1]?.body as string)).toEqual({ clienteId: null })
  })

  it('F6 ya no está pendiente: lleva al NIT del cliente (HU-06.17)', () => {
    expect(ATAJOS.find((atajo) => atajo.tecla === 'F6')?.accion).toBe('asignarCliente')
    expect(ACCIONES_PENDIENTES.asignarCliente).toBeUndefined()
  })
})
