import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { claveClienteDeVenta, editarCliente, type Cliente } from '../../api/clientes'
import { ClienteDeLaVenta } from './ClienteDeLaVenta'
import { EdicionCliente } from './EdicionCliente'
import { clienteDeLista, IDENTIFICACION_NO_EDITABLE, identificacionDe } from './textos'

const CLAVO: Cliente = {
  id: 5,
  nit: '5769370',
  nombre: 'Ferretería El Clavo',
  direccion: 'Zona 1',
  correo: 'compras@elclavo.gt',
  tipoIdentificacion: 'NIT',
}

const html = (contenido: ReactNode, cliente = new QueryClient()) =>
  renderToString(<QueryClientProvider client={cliente}>{contenido}</QueryClientProvider>).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('editar los datos de un cliente (HU-05.09)', () => {
  it('ofrece nombre, dirección y correo con sus valores actuales (crit. 1)', () => {
    const formulario = html(<EdicionCliente cliente={CLAVO} onGuardado={() => {}} onCancelar={() => {}} />)

    expect(formulario).toContain('aria-label="Editar cliente"')
    expect(formulario).toMatch(/name="nombreCliente"[^>]*value="Ferretería El Clavo"/)
    expect(formulario).toMatch(/name="direccionCliente"[^>]*value="Zona 1"/)
    expect(formulario).toMatch(/name="correoCliente"[^>]*value="compras@elclavo.gt"/)
    expect(formulario).toContain('Guardar cambios')
  })

  it('muestra el NIT o CUI sin campo para editarlo (crit. 2)', () => {
    const formulario = html(<EdicionCliente cliente={CLAVO} onGuardado={() => {}} onCancelar={() => {}} />)

    expect(formulario).toContain('<strong>NIT 576937-0</strong>')
    expect(formulario).toContain(IDENTIFICACION_NO_EDITABLE)
    expect(formulario).not.toContain('name="nit"')
    expect(identificacionDe({ nit: '1234567890101', tipoIdentificacion: 'CUI' })).toBe('CUI 1234567890101')
  })

  it('se puede editar durante la venta, desde el panel del cliente (crit. 1)', () => {
    const consultas = new QueryClient()
    consultas.setQueryData(claveClienteDeVenta(31), { consumidorFinal: false, cliente: CLAVO, correoFactura: null })
    expect(html(<ClienteDeLaVenta ventaId={31} />, consultas)).toContain('data-control="editar-cliente"')

    const aCf = new QueryClient()
    aCf.setQueryData(claveClienteDeVenta(31), { consumidorFinal: true, cliente: null, correoFactura: null })
    expect(html(<ClienteDeLaVenta ventaId={31} />, aCf)).not.toContain('data-control="editar-cliente"')
  })

  it('convierte el cliente de la lista para editarlo', () => {
    expect(
      clienteDeLista({
        id: 5,
        tipoIdentificacion: 'NIT',
        identificacion: '5769370',
        nombre: 'Ferretería El Clavo',
        direccion: 'Zona 1',
        correo: null,
        ultimaCompra: null,
      }),
    ).toEqual({ ...CLAVO, correo: null })
  })

  it('envía solo nombre, dirección y correo a la ruta del cliente', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(CLAVO), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await editarCliente(5, { nombre: 'Ferretería El Clavo', direccion: 'Zona 1', correo: '' })

    const [ruta, opciones] = respuesta.mock.calls[0] as unknown as [string, RequestInit]
    expect(ruta).toBe('/api/clientes/5')
    expect(opciones.method).toBe('PUT')
    expect(JSON.parse(opciones.body as string)).toEqual({ nombre: 'Ferretería El Clavo', direccion: 'Zona 1', correo: '' })
  })
})
