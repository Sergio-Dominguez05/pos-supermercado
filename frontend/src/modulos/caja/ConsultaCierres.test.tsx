import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  consultarCierres,
  obtenerDetalleCierre,
  rutaCierres,
  type CierreDeTurno,
  type DetalleCierre,
  type PaginaCierres,
} from '../../api/turnos'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { diaEnGuatemala, filtrosIniciales, rotuloCierres } from './cierres'
import { FiltrosDeCierres, PaginaConsultaCierres, TablaCierres, VistaDetalleCierre } from './ConsultaCierres'

const CIERRE: CierreDeTurno = {
  turnoId: 12,
  cajaId: 2,
  cajaNombre: 'Caja 2',
  usuarioId: 7,
  usuarioNombre: 'Lucía Méndez',
  abiertoEn: '2026-11-20T14:00:00Z',
  cerradoEn: '2026-11-20T22:30:00Z',
  cerradoPor: 'Ana Supervisora',
  totalRegistrado: 150,
  totalLote: 149,
  diferencia: -1,
  justificacion: 'Faltó un voucher',
}

const PAGINA: PaginaCierres = { cierres: [CIERRE], pagina: 0, tamanoPagina: 50, total: 1 }

const DETALLE: DetalleCierre = {
  cierre: CIERRE,
  resumen: {
    ventas: 4,
    debito: 100,
    credito: 50,
    anuladas: 0,
    totalAnulado: 0,
    totalRegistrado: 150,
    contingencias: 0,
  },
}

const renderizar = (contenido: ReactNode, permisos: string[] = ['CAJA_VER_CIERRES']) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, { rol: 'SUPERVISOR', permisos } satisfies Partial<UsuarioSesion>)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('consultar cierres anteriores (HU-09.13)', () => {
  it('lista fecha, caja, cajero, registrado, cierre de lote y diferencia, con enlace al detalle (crit. 1)', () => {
    const html = renderizar(<TablaCierres pagina={PAGINA} onPagina={() => {}} />)

    for (const columna of ['Cierre', 'Caja', 'Cajero', 'Registrado', 'Cierre de lote', 'Diferencia'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('href="/caja/cierres/12"')
    expect(html).toContain('<td>Caja 2</td><td>Lucía Méndez</td><td>Q150.00</td><td>Q149.00</td>')
    expect(html).toContain('<td class="error">Q-1.00</td>')
    expect(html).toContain('Mostrando 1–1 de 1 cierre')
    expect(rotuloCierres(0, 50, 0, 0)).toBe('No hay cierres con estos filtros.')
  })

  it('filtra por fecha, caja, cajero y diferencias; abre con los últimos 7 días (crit. 2)', () => {
    const ahora = new Date('2026-11-20T23:30:00-06:00')
    const iniciales = filtrosIniciales(ahora)
    expect(iniciales).toEqual({
      desde: '2026-11-14',
      hasta: '2026-11-20',
      cajaId: '',
      cajeroId: '',
      soloConDiferencias: false,
    })
    // A las 23:30 de Guatemala ya es otro día en UTC: cuenta el de Guatemala.
    expect(diaEnGuatemala(ahora)).toBe('2026-11-20')

    const html = renderizar(
      <FiltrosDeCierres
        iniciales={iniciales}
        opciones={{ cajas: [{ id: 2, etiqueta: 'Caja 2' }], cajeros: [{ id: 7, etiqueta: 'Lucía Méndez (lucia)' }] }}
        onBuscar={() => {}}
      />,
    )
    expect(html).toContain('value="2026-11-14"')
    expect(html).toContain('<option value="2">Caja 2</option>')
    expect(html).toContain('<option value="7">Lucía Méndez (lucia)</option>')
    expect(html).toContain('name="soloConDiferencias"')

    expect(rutaCierres({ ...iniciales, cajaId: '2', soloConDiferencias: true }, 1)).toBe(
      '/api/cierres?desde=2026-11-14&hasta=2026-11-20&cajaId=2&soloConDiferencias=true&pagina=1',
    )
    expect(rutaCierres({ desde: '', hasta: '', cajaId: '', cajeroId: '', soloConDiferencias: false }, 0)).toBe(
      '/api/cierres',
    )
  })

  it('el detalle trae el cierre completo, lo cobrado y la reimpresión (crit. 3)', async () => {
    const html = renderizar(<VistaDetalleCierre detalle={DETALLE} />)

    expect(html).toContain('<h2>Caja 2 · Lucía Méndez</h2>')
    expect(html).toContain('<dt>Cerrado por</dt><dd>Ana Supervisora</dd>')
    expect(html).toContain('<dt>Justificación</dt><dd>Faltó un voucher</dd>')
    expect(html).toContain('Tarjeta de débito')
    expect(html).toContain('Reimprimir el comprobante</button>')

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })),
    )
    await obtenerDetalleCierre(12)
    await consultarCierres(filtrosIniciales(new Date('2026-11-20T12:00:00-06:00')), 0)
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/cierres/12')
    expect(vi.mocked(fetch).mock.calls[1][0]).toBe('/api/cierres?desde=2026-11-14&hasta=2026-11-20')
  })

  it('solo quien ve los cierres de los demás tiene la pantalla y la opción del menú', () => {
    expect(renderizar(<PaginaConsultaCierres />, ['CAJA_OPERAR_PROPIA'])).toContain(
      'No tiene permiso para consultar los cierres de caja.',
    )
    expect(opcionesPara(['CAJA_VER_CIERRES'])).toEqual([
      expect.objectContaining({ etiqueta: 'Cierres de caja', ruta: '/caja/cierres' }),
    ])
  })
})
