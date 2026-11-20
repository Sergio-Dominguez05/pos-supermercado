import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  claveResumenTurno,
  listarTurnosAbiertos,
  obtenerResumenTurno,
  type ResumenDeTurno,
  type Turno,
} from '../../api/turnos'
import { CargaResumen, ListaTurnosAbiertos } from './ResumenTurnos'

const PROPIO: Turno = {
  id: 7,
  cajaId: 2,
  cajaNombre: 'Caja 2',
  usuarioNombre: 'Ana López',
  estado: 'ABIERTO',
  abiertoEn: '2026-11-20T14:00:00Z',
  propio: true,
}

const AJENO: Turno = {
  ...PROPIO,
  id: 8,
  cajaId: 3,
  cajaNombre: 'Caja 3',
  usuarioNombre: 'Luis Pérez',
  propio: false,
}

const RESUMEN: ResumenDeTurno = {
  ventas: 3,
  debito: 10,
  credito: 5,
  anuladas: 1,
  totalAnulado: 3,
  totalRegistrado: 15,
  contingencias: 0,
}

const renderizar = (contenido: ReactNode, resumen?: ResumenDeTurno) => {
  const cliente = new QueryClient()
  if (resumen) cliente.setQueryData(claveResumenTurno(8), resumen)
  return renderToString(
    <QueryClientProvider client={cliente}>{contenido}</QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => vi.unstubAllGlobals())

describe('resumen de turnos abiertos (HU-09.06)', () => {
  it('permite elegir un turno e identifica el propio', () => {
    const html = renderizar(
      <ListaTurnosAbiertos turnos={[PROPIO, AJENO]} seleccionado={8} onSeleccionar={() => undefined} />,
    )

    for (const columna of ['Caja', 'Cajero', 'Apertura']) expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('Ana López · Propio')
    expect(html).toContain('Luis Pérez')
    expect(html).toContain('Ver resumen</button>')
    expect(html).toContain('Resumen visible</button>')
  })

  it('consulta los turnos visibles y el resumen seleccionado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })))

    await listarTurnosAbiertos()
    await obtenerResumenTurno(8)

    const llamadas = vi.mocked(fetch).mock.calls
    expect(llamadas[0][0]).toBe('/api/turnos/abiertos')
    expect(llamadas[1][0]).toBe('/api/turnos/8/resumen')
  })

  it('permite actualizar un resumen que ya está visible', () => {
    const html = renderizar(<CargaResumen turnoId={8} />, RESUMEN)

    expect(html).toContain('<strong>Q15.00</strong>')
    expect(html).toContain('Actualizar resumen</button>')
  })
})
