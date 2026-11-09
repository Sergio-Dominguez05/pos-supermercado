import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { CLAVE_TURNO_ACTUAL, type Turno } from '../../api/turnos'
import { puedeVender } from '../caja/turno'
import { CLAVE_SESION } from '../login/sesion'
import { Venta } from './Venta'

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

const renderizar = (sesion: UsuarioSesion, turno: Turno | null) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  cliente.setQueryData(CLAVE_TURNO_ACTUAL, turno)
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
