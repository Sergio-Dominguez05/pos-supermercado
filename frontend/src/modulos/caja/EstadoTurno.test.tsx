import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { abrirTurno, CLAVE_TURNO_ACTUAL, turnoActual, type Turno } from '../../api/turnos'
import { CLAVE_SESION } from '../login/sesion'
import { EstadoTurno } from './EstadoTurno'
import { horaDe, pedidoAbrirTurno, turnoAbierto, turnoDeOtroUsuario } from './textos'

const CAJERO: Partial<UsuarioSesion> = {
  rol: 'CAJERO',
  nombreCompleto: 'Ana López',
  permisos: ['VENTA_REGISTRAR', 'CAJA_OPERAR_PROPIA'],
  cajaNombre: 'Caja 1',
}

const TURNO: Turno = {
  id: 7,
  cajaId: 1,
  cajaNombre: 'Caja 1',
  usuarioNombre: 'Ana López',
  estado: 'ABIERTO',
  abiertoEn: '2026-11-08T14:05:00Z',
  propio: true,
}

const renderizar = (sesion: Partial<UsuarioSesion>, turno?: Turno | null) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  if (turno !== undefined) cliente.setQueryData(CLAVE_TURNO_ACTUAL, turno)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <EstadoTurno />
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('estado del turno (HU-09.01)', () => {
  it('en una caja cerrada pide abrir el turno (crit. 1)', () => {
    const html = renderizar(CAJERO, null)

    expect(html).toContain('Caja 1 está cerrada. Abra su turno para empezar a vender.')
    expect(html).toContain('Abrir turno</button>')
  })

  it('con el turno propio abierto muestra desde qué hora (crit. 2)', () => {
    const html = renderizar(CAJERO, TURNO)

    expect(html).toContain('Su turno en Caja 1 está abierto desde las 08:05.')
    expect(html).not.toContain('</button>')
  })

  it('si la caja tiene el turno de otro usuario no ofrece abrir otro', () => {
    const html = renderizar(CAJERO, { ...TURNO, propio: false, usuarioNombre: 'Luis Pérez' })

    expect(html).toContain('Caja 1 tiene abierto el turno de Luis Pérez. Primero se debe cerrar ese turno.')
    expect(html).not.toContain('</button>')
  })

  it('no muestra nada en un equipo sin caja ni a quien no opera caja', () => {
    expect(renderizar({ ...CAJERO, cajaNombre: null }, null)).toBe('')
    expect(renderizar({ ...CAJERO, permisos: ['USUARIO_ADMINISTRAR'] }, null)).toBe('')
  })

  it('los textos usan la hora de Guatemala', () => {
    expect(horaDe('2026-11-08T22:30:00Z')).toBe('16:30')
    expect(pedidoAbrirTurno('Caja 2')).toBe('Caja 2 está cerrada. Abra su turno para empezar a vender.')
    expect(turnoAbierto(TURNO)).toContain('08:05')
    expect(turnoDeOtroUsuario({ ...TURNO, usuarioNombre: 'Luis' })).toContain('el turno de Luis')
  })
})

describe('api de turnos (HU-09.01)', () => {
  it('abrir el turno es un POST sin cuerpo: no hay fondo inicial (crit. 3)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(TURNO), { status: 201 })))

    await abrirTurno()

    const [url, opciones] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/turnos')
    expect(opciones?.method).toBe('POST')
    expect(opciones?.body).toBeUndefined()
  })

  it('una caja cerrada (204) se lee como null', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(turnoActual()).resolves.toBeNull()
  })
})
