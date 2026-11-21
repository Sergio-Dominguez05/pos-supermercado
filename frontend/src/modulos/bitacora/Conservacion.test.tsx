import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { archivarBitacora, cambiarPlazoConservacion, type ConservacionBitacora } from '../../api/bitacora'
import { EstadoConservacion } from './Conservacion'

const ESTADO: ConservacionBitacora = {
  plazoAnios: 5,
  plazoMinimoAnios: 4,
  registrosActivos: 1200,
  registrosArchivados: 300,
  registroMasAntiguo: '2025-01-02T15:00:00Z',
  archivadoHasta: '2025-06-30T23:00:00Z',
  archivableAntesDe: '2026-08-22',
  plazoCumplidoAntesDe: '2021-11-20',
}

const html = (contenido: React.ReactNode) =>
  renderToString(<QueryClientProvider client={new QueryClient()}>{contenido}</QueryClientProvider>).replaceAll(
    '<!-- -->',
    '',
  )

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('conservar la bitácora (HU-11.10)', () => {
  it('muestra el plazo y aclara que nunca se elimina nada (crit. 1 y 3)', () => {
    const vista = html(<EstadoConservacion estado={ESTADO} esAdministrador={false} />)

    expect(vista).toContain('por lo menos <strong>5 años</strong>')
    expect(vista).toContain('el sistema nunca los elimina')
    expect(vista).toContain('<dt>Registros archivados</dt><dd>300</dd>')
    expect(vista).not.toContain('name="plazoAnios"')
    expect(vista).not.toContain('Archivar lo anterior al')
  })

  it('el Administrador cambia el plazo y archiva lo de hace más de 90 días (crit. 1 y 2)', () => {
    const vista = html(<EstadoConservacion estado={ESTADO} esAdministrador />)

    const campo = (nombre: string) => vista.match(new RegExp(`<input[^>]*name="${nombre}"[^>]*>`))?.[0] ?? ''
    expect(campo('plazoAnios')).toContain('min="4"')
    expect(campo('plazoAnios')).toContain('max="50"')
    expect(campo('plazoAnios')).toContain('value="5"')
    expect(campo('antesDe')).toContain('max="2026-08-22"')
    expect(campo('antesDe')).toContain('value="2026-08-22"')
    expect(vista).toContain('Solo registros de hace más de 90 días.')
  })

  it('usa las rutas de configuración de la bitácora', async () => {
    const respuesta = vi.fn(async () => new Response(JSON.stringify(ESTADO), { status: 200 }))
    vi.stubGlobal('fetch', respuesta)

    await cambiarPlazoConservacion(7)
    await archivarBitacora('2025-01-01')

    const llamadas = respuesta.mock.calls.map((llamada) => llamada as unknown as [string, RequestInit])
    expect(llamadas.map(([ruta, opciones]) => `${opciones.method} ${ruta} ${opciones.body}`)).toEqual([
      'PUT /api/configuracion/bitacora {"plazoAnios":7}',
      'POST /api/configuracion/bitacora/archivo {"antesDe":"2025-01-01"}',
    ])
  })
})
