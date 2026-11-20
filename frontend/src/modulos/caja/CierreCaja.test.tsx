import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  cerrarCaja,
  compararCierre,
  listarCierres,
  reimprimirCierre,
  type CierreDeTurno,
  type ResumenDeTurno,
} from '../../api/turnos'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioCierre, ListaCierres, PaginaCierreCaja, ResumenCobrado } from './CierreCaja'
import { confirmacionCierre, diferenciaDeCierre, totalLoteValido } from './textos'

const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['CAJA_OPERAR_PROPIA'], cajaNombre: null }

const RESUMEN: ResumenDeTurno = { ventas: 3, debito: 10, credito: 5, anuladas: 1, totalAnulado: 3,
  totalRegistrado: 15, contingencias: 1 }

const CIERRE: CierreDeTurno = { turnoId: 9, cajaId: 1, cajaNombre: 'Caja 1', usuarioId: 4, usuarioNombre: 'Ana López',
  abiertoEn: '2026-11-19T14:00:00Z', cerradoEn: '2026-11-19T23:00:00Z', cerradoPor: 'Ana López', totalRegistrado: 20,
  totalLote: 18, diferencia: -2, justificacion: 'Un pago se registró dos veces' }

const renderizar = (contenido: ReactNode, sesion = CAJERO) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}
const sinAccion = () => undefined

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cierre de caja (HU-09.08 a HU-09.11)', () => {
  it('muestra lo cobrado por tarjeta, las anuladas y el total registrado (09.08 crit. 3)', () => {
    const html = renderizar(<ResumenCobrado resumen={RESUMEN} />)

    expect(html).toContain('<dt>Tarjeta de débito</dt><dd>Q10.00</dd>')
    expect(html).toContain('<dt>Tarjeta de crédito</dt><dd>Q5.00</dd>')
    expect(html).toContain('Facturas anuladas (1)')
    expect(html).toContain('<strong>Q15.00</strong>')
    expect(html).toContain('1 por certificar')
  })

  it('pide el total del cierre de lote (09.08 crit. 2)', () => {
    const html = renderizar(<FormularioCierre resumen={RESUMEN} cajaNombre="Caja 1" onCerrada={sinAccion} />)

    expect(html).toMatch(/<input[^>]*name="totalLote"[^>]*>/)
    expect(html).toContain('Cerrar la caja</button>')
  })

  it('en cierre ciego no muestra el resumen antes de confirmar el monto (09.11 crit. 1)', () => {
    const html = renderizar(
      <FormularioCierre resumen={null} cierreCiego cajaNombre="Caja 1" onCerrada={sinAccion} />,
    )

    expect(html).not.toContain('Total registrado')
    expect(html).not.toContain('Q15.00')
    expect(html).toContain('confirme el total del cierre de lote')
    expect(html).toContain('Confirmar monto</button>')
  })

  it('la diferencia es lote menos registrado, sin errores de redondeo (09.08 crit. 3 y 09.09)', () => {
    expect(diferenciaDeCierre(18, 20)).toBe(-2)
    expect(diferenciaDeCierre(10.3, 10.1)).toBe(0.2)
    expect(diferenciaDeCierre(15, 15)).toBe(0)
    expect(totalLoteValido('12.50')).toBe(true)
    expect(totalLoteValido('12.505')).toBe(false)
    expect(totalLoteValido('-1')).toBe(false)
    expect(confirmacionCierre('Caja 1', 0)).toBe(
      '¿Cerrar la caja Caja 1? El cierre de lote coincide con lo registrado. Después de cerrarla no se puede ' +
        'modificar el turno.',
    )
  })

  it('lista los cierres anteriores con su diferencia, justificación y reimpresión (09.10 crit. 3)', () => {
    const html = renderizar(<ListaCierres cierres={[CIERRE]} />)

    for (const columna of ['Caja y cajero', 'Cierre', 'Registrado', 'Cierre de lote', 'Diferencia'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('Caja 1 · Ana López')
    expect(html).toContain('Un pago se registró dos veces')
    expect(html).toContain('Reimprimir</button>')
  })

  it('sin turno abierto no hay nada que cerrar, y la opción Caja del menú lleva aquí', () => {
    expect(renderizar(<PaginaCierreCaja />)).toContain('No tiene un turno abierto en esta caja')
    expect(opcionesPara(['CAJA_OPERAR_PROPIA'])).toEqual([
      expect.objectContaining({ etiqueta: 'Caja', ruta: '/caja' }),
    ])
  })

  it('cierra, lista y reimprime con las rutas del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await compararCierre(18)
    await cerrarCaja(18, ' Un pago repetido ')
    await cerrarCaja(15, '  ')
    await listarCierres()
    await reimprimirCierre(9)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/turnos/actual/cierre/comparar')
    expect(JSON.parse(llamadas[0][1]?.body as string)).toEqual({ totalLote: 18, justificacion: null })
    expect(llamadas[1][0]).toBe('/api/turnos/actual/cerrar')
    expect(JSON.parse(llamadas[1][1]?.body as string)).toEqual({ totalLote: 18, justificacion: 'Un pago repetido' })
    expect(JSON.parse(llamadas[2][1]?.body as string)).toEqual({ totalLote: 15, justificacion: null })
    expect(llamadas[3][0]).toBe('/api/turnos/cierres')
    expect(llamadas[4][0]).toBe('/api/turnos/9/cierre/reimprimir')
  })
})
