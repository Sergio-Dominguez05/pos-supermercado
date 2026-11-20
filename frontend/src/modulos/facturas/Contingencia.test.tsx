import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  certificarContingencia,
  CLAVE_CONTINGENCIA,
  CLAVE_ESTADO_CONTINGENCIA,
  obtenerContingencia,
  type ResumenContingencia,
} from '../../api/facturas'
import { CLAVE_SESION } from '../login/sesion'
import {
  AvisoContingenciaSupervisor,
  AvisoContingenciaVenta,
  PaginaContingencia,
  ResumenDeContingencia,
} from './Contingencia'
import { avisoContingenciaSupervisor, avisoContingenciaVenta } from './textos'

const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] }

const RESUMEN: ResumenContingencia = { pendientes: 2, masAntigua: '2026-11-06T16:00:00Z', diasMasAntigua: 13,
  venceMasAntigua: '2026-11-21', alerta: true, diasMaximos: 15, diaDeAlerta: 12,
  lista: [{ ventaId: 31, ventaNumero: 1042, cajaNombre: 'Caja 1', estado: 'CONTINGENCIA', numeroAcceso: 100000007,
    emitidaEn: '2026-11-06T16:00:00Z', total: 15, intentos: 3, ultimoError: 'Sin conexión con el certificador.',
    proximoIntento: '2026-11-19T16:04:00Z', dias: 13, vence: '2026-11-21', alerta: true }] }

const renderizar = (contenido: ReactNode, datos: { estado?: unknown; resumen?: unknown } = {}, sesion = CAJERO) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  if (datos.estado) cliente.setQueryData(CLAVE_ESTADO_CONTINGENCIA, datos.estado)
  if (datos.resumen) cliente.setQueryData(CLAVE_CONTINGENCIA, datos.resumen)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('contingencia (HU-08.07 a HU-08.09)', () => {
  it('el cajero ve que se opera en contingencia, y nada si no hay pendientes (HU-08.07 crit. 3)', () => {
    expect(renderizar(<AvisoContingenciaVenta />, { estado: { enContingencia: true, pendientes: 2 } })).toContain(
      'Operando en contingencia: hay 2 comprobantes pendientes de certificar.',
    )
    expect(renderizar(<AvisoContingenciaVenta />, { estado: { enContingencia: false, pendientes: 0 } })).toBe('')
    expect(avisoContingenciaVenta(1)).toContain('hay 1 comprobante pendiente de certificar')
  })

  it('el supervisor ve cuántos hay y el más antiguo, destacado cerca del plazo (HU-08.09 crit. 1 y 2)', () => {
    const conAlerta = renderizar(<AvisoContingenciaSupervisor />, { resumen: RESUMEN })
    const sinAlerta = renderizar(<AvisoContingenciaSupervisor />, { resumen: { ...RESUMEN, alerta: false } })

    expect(conAlerta).toContain('class="tarjeta error"')
    expect(conAlerta).toContain('Hay 2 comprobantes en contingencia. El más antiguo tiene 13 días; vence el 21/11/2026.')
    expect(conAlerta).toContain('href="/facturas/contingencia"')
    expect(sinAlerta).toContain('class="tarjeta pendiente"')
    expect(renderizar(<AvisoContingenciaSupervisor />, { resumen: { ...RESUMEN, pendientes: 0 } })).toBe('')
    expect(avisoContingenciaSupervisor(1, 1, null)).toBe('Hay 1 comprobante en contingencia. El más antiguo tiene 1 día.')
  })

  it('lista cada pendiente con su número de acceso y el error del último intento (HU-08.09 crit. 3)', () => {
    const html = renderizar(<ResumenDeContingencia resumen={RESUMEN} />)

    for (const columna of ['Venta', 'Número de acceso', 'Emitida', 'Días', 'Total', 'Último intento'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('N.º 1042 · Caja 1')
    expect(html).toContain('100000007')
    expect(html).toContain('<strong class="error">13</strong>')
    expect(html).toContain('3 intentos')
    expect(html).toContain('Sin conexión con el certificador.')
    expect(html).toContain('Certificar pendientes ahora')
  })

  it('solo quien gestiona facturas ve la pantalla', () => {
    expect(renderizar(<PaginaContingencia />)).toContain('No tiene permiso para gestionar la contingencia.')
  })

  it('consulta y certifica con las rutas del servidor (HU-08.08 crit. 4)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await obtenerContingencia()
    await certificarContingencia()

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/facturas/contingencia')
    expect(llamadas[1][0]).toBe('/api/facturas/contingencia/certificar')
    expect(llamadas[1][1]).toMatchObject({ method: 'POST' })
  })
})
