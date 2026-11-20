import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { cerrarTurnoDeOtro, type ResumenDeTurno, type TurnoAbierto } from '../../api/turnos'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { FormularioCierre } from './CierreCaja'
import { ListaTurnosAbiertos, PaginaTurnosAbiertos } from './TurnosAbiertos'

const TURNO: TurnoAbierto = {
  id: 12,
  cajaId: 2,
  cajaNombre: 'Caja 2',
  usuarioId: 7,
  usuarioNombre: 'Lucía Méndez',
  abiertoEn: '2026-11-20T14:00:00Z',
}

const RESUMEN: ResumenDeTurno = {
  ventas: 3,
  debito: 10,
  credito: 5,
  anuladas: 0,
  totalAnulado: 0,
  totalRegistrado: 15,
  contingencias: 0,
}

const renderizar = (contenido: ReactNode, permisos: string[] = ['CAJA_CERRAR_OTRA']) => {
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

describe('cerrar la caja de otro usuario (HU-09.12)', () => {
  it('lista los turnos abiertos de los demás para elegir cuál cerrar (crit. 1)', () => {
    const html = renderizar(<ListaTurnosAbiertos turnos={[TURNO]} onElegir={() => {}} />)

    expect(html).toContain('<td>Caja 2</td><td>Lucía Méndez</td>')
    expect(html).toContain('aria-label="Cerrar el turno de Lucía Méndez en Caja 2"')
    expect(renderizar(<ListaTurnosAbiertos turnos={[]} onElegir={() => {}} />)).toContain(
      'No hay turnos abiertos de otros usuarios.',
    )
  })

  it('usa el mismo formulario del cierre propio, con el turno elegido', () => {
    const html = renderizar(
      <FormularioCierre
        resumen={RESUMEN}
        cajaNombre="Caja 2"
        titulo="Cerrar el turno de Lucía Méndez en Caja 2"
        cerrar={() => Promise.reject(new Error('no se usa'))}
        onCerrada={() => {}}
      />,
    )
    expect(html).toContain('<h2>Cerrar el turno de Lucía Méndez en Caja 2</h2>')
    expect(html).toContain('Total del cierre de lote de la terminal')
    expect(html).toContain('<strong>Q15.00</strong>')
  })

  it('cierra el turno elegido con el total del lote y la justificación (crit. 1 y 2)', async () => {
    const respuesta = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await cerrarTurnoDeOtro(12, 14.5, '  Faltó un voucher ')

    expect(respuesta).toHaveBeenCalledWith(
      '/api/turnos/12/cerrar',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ totalLote: 14.5, justificacion: 'Faltó un voucher' }),
      }),
    )
  })

  it('solo quien puede cerrar la caja de otro ve la pantalla y la opción (crit. 3)', () => {
    expect(renderizar(<PaginaTurnosAbiertos />, ['CAJA_OPERAR_PROPIA'])).toContain(
      'No tiene permiso para cerrar la caja de otro usuario.',
    )
    expect(opcionesPara(['CAJA_CERRAR_OTRA'])).toEqual([
      expect.objectContaining({ etiqueta: 'Cerrar caja de otro usuario', ruta: '/caja/turnos' }),
    ])
  })
})
