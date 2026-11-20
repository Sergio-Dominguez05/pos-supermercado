import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  asignarClienteAVenta,
  buscarClientePorCui,
  buscarClientePorNit,
  cambiarCorreoDeFactura,
  claveClienteDeVenta,
  registrarCliente,
  type BusquedaNit,
  type Cliente,
  type ClienteDeVenta,
} from '../../api/clientes'
import { ATAJOS, ACCIONES_PENDIENTES } from '../../atajos/atajos'
import { ClienteDeLaVenta, CorreoDeFactura, RegistroCliente } from './ClienteDeLaVenta'
import {
  avisoBusqueda,
  avisoCorreoFactura,
  CONSUMIDOR_FINAL,
  correoValido,
  cuiValido,
  CUI_INVALIDO,
  describirCliente,
  nitConGuion,
} from './textos'

const FERRETERIA: Cliente = { id: 5, nit: '5769370', nombre: 'Ferretería El Clavo', direccion: 'Ciudad', correo: null,
  tipoIdentificacion: 'NIT' }

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
  ({ origen, nit: '5769370', cliente, mensaje, tipoIdentificacion: 'NIT' })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cliente de la venta (EP-05)', () => {
  it('cada venta empieza a Consumidor Final y vuelve a CF con un clic (HU-05.01 crit. 1 y 2)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, {
      consumidorFinal: true,
      cliente: null,
      correoFactura: null,
    })

    expect(html).toContain(`<strong>${CONSUMIDOR_FINAL}</strong>`)
    expect(html).toContain('data-control="consumidor-final"')
    expect(html).toContain('<kbd>F6</kbd>')
    expect(campo(html, 'nit')).not.toBe('')
  })

  it('muestra el NIT, el nombre y la dirección del cliente asignado (HU-05.02 crit. 1)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, {
      consumidorFinal: false,
      cliente: FERRETERIA,
      correoFactura: null,
    })

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
    const html = renderizar(<RegistroCliente nit="5769370" onRegistrado={sinAccion} onConsumidorFinal={sinAccion} />, {
      consumidorFinal: true,
      cliente: null,
      correoFactura: null,
    })

    expect(campo(html, 'nombreCliente')).toContain('required')
    expect(campo(html, 'direccionCliente')).not.toContain('required')
    expect(campo(html, 'direccionCliente')).toContain('placeholder="Ciudad"')
    expect(campo(html, 'correoCliente')).toContain('type="email"')
    expect(html).toContain('Registrar y usar')
    expect(html).toContain('Facturar como CF')
  })

  it('busca, registra y asigna con las rutas del servidor', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })),
    )

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

  it('dice a dónde se enviará la factura y permite cambiar el correo o no enviarla (HU-05.11 crit. 1 y 4)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, {
      consumidorFinal: false,
      cliente: { ...FERRETERIA, correo: 'ventas@clavo.test' },
      correoFactura: 'ventas@clavo.test',
    })

    expect(html).toContain('La factura se enviará a ventas@clavo.test.')
    expect(html).toContain('>Cambiar correo</button>')
    expect(html).toContain('data-control="no-enviar-correo"')
  })

  it('sin correo no insiste: solo ofrece agregarlo (HU-05.11 crit. 2)', () => {
    const html = renderizar(<CorreoDeFactura correo={null} onCambiado={sinAccion} />, {
      consumidorFinal: true,
      cliente: null,
      correoFactura: null,
    })

    expect(html).toContain('Sin correo: la factura solo se imprime.')
    expect(html).toContain('>Agregar correo</button>')
    expect(html).not.toContain('No enviar')
    expect(html).not.toContain('role="alert"')
    expect(avisoCorreoFactura(null)).toBe('Sin correo: la factura solo se imprime.')
  })

  it('valida la forma del correo como el servidor (HU-05.11 crit. 3)', () => {
    for (const valido of ['ana@cliente.test', ' ana.lopez@correo.com.gt ']) expect(correoValido(valido)).toBe(true)
    for (const invalido of ['ana', 'ana@cliente', 'ana @cliente.test', '@cliente.test', ''])
      expect(correoValido(invalido)).toBe(false)
  })

  it('guarda el correo de la factura con la ruta del servidor (HU-05.11)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })),
    )

    await cambiarCorreoDeFactura('  ana@cliente.test ')

    const [ruta, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(ruta).toBe('/api/ventas/actual/cliente/correo')
    expect(opciones).toMatchObject({ method: 'PUT' })
    expect(JSON.parse(opciones?.body as string)).toEqual({ correo: 'ana@cliente.test' })
  })

  it('elige NIT o CUI; el CUI se valida con 13 dígitos (HU-05.06 crit. 1 y 2)', () => {
    const html = renderizar(<ClienteDeLaVenta ventaId={31} />, {
      consumidorFinal: true,
      cliente: null,
      correoFactura: null,
    })
    expect(html).toContain('name="tipoIdentificacion"')
    expect(html).toContain('<option value="NIT" selected="">NIT</option>')
    expect(html).toContain('<option value="CUI">CUI</option>')

    for (const valido of ['1234567890101', '1234 56789 0101', '1234-56789-0101']) expect(cuiValido(valido)).toBe(true)
    for (const invalido of ['123456789010', '12345678901012', '12345678901AB']) expect(cuiValido(invalido)).toBe(false)
    expect(CUI_INVALIDO).toBe('El CUI debe tener 13 dígitos numéricos.')
  })

  it('el cliente con CUI se muestra con su CUI y se registra como CUI (HU-05.06 crit. 3)', async () => {
    expect(describirCliente({ ...FERRETERIA, nit: '1234567890101', tipoIdentificacion: 'CUI' })).toBe(
      'CUI 1234567890101 · Ferretería El Clavo · Ciudad',
    )
    const html = renderizar(
      <RegistroCliente nit="1234567890101" tipo="CUI" onRegistrado={sinAccion} onConsumidorFinal={sinAccion} />,
      { consumidorFinal: true, cliente: null, correoFactura: null },
    )
    expect(campo(html, 'nombreCliente')).toContain('required')

    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))
    await buscarClientePorCui('1234 56789 0101')
    await registrarCliente({ tipoIdentificacion: 'CUI', nit: '1234567890101', nombre: 'Ana', direccion: '',
      correo: '' })
    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/clientes/cui/1234567890101')
    expect(JSON.parse(llamadas[1][1]?.body as string)).toMatchObject({
      tipoIdentificacion: 'CUI',
      nit: '1234567890101',
    })
  })

  it('F6 ya no está pendiente: lleva al NIT del cliente (HU-06.17)', () => {
    expect(ATAJOS.find((atajo) => atajo.tecla === 'F6')?.accion).toBe('asignarCliente')
    expect(ACCIONES_PENDIENTES.asignarCliente).toBeUndefined()
  })
})
