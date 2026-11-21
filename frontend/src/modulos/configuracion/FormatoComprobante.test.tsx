import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  guardarFormatoComprobante,
  vistaPreviaComprobante,
  type FormatoComprobante,
  type VistaPrevia,
} from '../../api/comprobante'
import { EdicionFormato, TicketDeMuestra } from './FormatoComprobante'
import { NavConfiguracion } from './NavConfiguracion'

const GUARDADO: FormatoComprobante = {
  encabezado: 'Precios bajos siempre',
  pie: '¡Gracias por su compra!',
  logo: 'data:image/png;base64,AAAA',
  logoAncho: 384,
  logoAlto: 96,
  modificadoEn: '2026-11-20T16:00:00Z',
}

const VISTA: VistaPrevia = {
  columnas: 32,
  logo: 'data:image/png;base64,BBBB',
  renglones: [
    { texto: 'Súper del Valle', alineacion: 'CENTRO', negrita: true, destacado: true },
    { texto: 'Precios bajos siempre', alineacion: 'CENTRO', negrita: false, destacado: false },
    { texto: 'TOTAL              Q10.00', alineacion: 'IZQUIERDA', negrita: true, destacado: false },
  ],
}

const html = (contenido: React.ReactNode) =>
  renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('formato del comprobante (HU-12.09)', () => {
  it('permite subir el logo y escribir los mensajes del encabezado y del pie (crit. 1 y 2)', () => {
    const formulario = html(<EdicionFormato guardado={GUARDADO} />)

    const campo = (nombre: string) => formulario.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''
    expect(campo('logo')).toContain('type="file"')
    expect(campo('logo')).toContain('accept="image/png,image/jpeg,image/gif,image/bmp"')
    expect(formulario).toContain('Quitar el logo actual')
    expect(campo('encabezado')).toContain('value="Precios bajos siempre"')
    expect(formulario).toContain('¡Gracias por su compra!</textarea>')
    expect(formulario).toContain('Los datos que exige la SAT y los totales (Subtotal, IVA y TOTAL) siempre se imprimen')
  })

  it('para guardar hay que ver antes la vista previa (crit. 3)', () => {
    const formulario = html(<EdicionFormato guardado={GUARDADO} />)

    expect(formulario).toMatch(/<button type="button" class="boton" disabled=""[^>]*>Guardar<\/button>/)
    expect(formulario).toContain('Use “Vista previa” para ver cómo saldrá la factura antes de guardar.')
  })

  it('la vista previa muestra el ticket con su logo en el ancho del papel (crit. 3)', () => {
    const ticket = html(<TicketDeMuestra vista={VISTA} />)

    expect(ticket).toContain('Vista previa (factura de ejemplo, 32 columnas)')
    expect(ticket).toContain('style="width:34ch"')
    expect(ticket).toContain('<img src="data:image/png;base64,BBBB" alt="Logo en blanco y negro"/>')
    expect(ticket).toContain('<div class="centro negrita destacado">Súper del Valle</div>')
    expect(ticket).toContain('<div class="negrita">TOTAL              Q10.00</div>')
  })

  it('aparece en la navegación de la configuración', () => {
    expect(html(<NavConfiguracion actual="Comprobante" />)).toContain('<strong>Comprobante</strong>')
    expect(html(<NavConfiguracion actual="Cajas" />)).toContain('href="/configuracion/comprobante"')
  })

  it('usa las rutas del servidor para la vista previa y para guardar', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(VISTA), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)
    const solicitud = { encabezado: 'Hola', pie: '', logo: null, quitarLogo: true }

    await vistaPreviaComprobante(solicitud, 58)
    await guardarFormatoComprobante(solicitud)

    const llamadas = respuesta.mock.calls.map((llamada) => llamada as unknown as [string, RequestInit])
    expect(llamadas.map(([ruta, opciones]) => `${opciones.method} ${ruta}`)).toEqual([
      'POST /api/configuracion/comprobante/vista-previa?anchoMm=58',
      'PUT /api/configuracion/comprobante',
    ])
    expect(JSON.parse(llamadas[1][1].body as string)).toEqual(solicitud)
  })
})
