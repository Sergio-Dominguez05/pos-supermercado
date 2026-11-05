import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import { verificarBitacora } from '../../api/bitacora'
import { opcionesPara } from '../inicio/opciones'
import { CLAVE_SESION } from '../login/sesion'
import { Bitacora, ResultadoVerificacion } from './Bitacora'

const renderizar = (contenido: ReactNode, sesion?: Partial<UsuarioSesion>) => {
  const cliente = new QueryClient()
  if (sesion) cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('bitácora (HU-11.05)', () => {
  it('ofrece verificar la integridad y ninguna opción para editar ni borrar (crit. 1 y 3)', () => {
    const html = renderizar(<Bitacora />, { rol: 'SUPERVISOR', permisos: ['BITACORA_CONSULTAR'] })

    expect(html).toContain('Verificar integridad')
    expect(html).not.toMatch(/Editar|Eliminar|Borrar/)
  })

  it('sin el permiso de consultar la bitácora no muestra nada', () => {
    const html = renderizar(<Bitacora />, { rol: 'CAJERO', permisos: ['VENTA_REGISTRAR'] })

    expect(html).toContain('No tiene permiso para consultar la bitácora.')
    expect(html).not.toContain('Verificar integridad')
  })

  it('informa una bitácora íntegra con su cantidad de registros', () => {
    const html = renderizar(<ResultadoVerificacion resultado={{ integra: true, registros: 102, problemas: [] }} />)

    expect(html).toContain('La bitácora está íntegra: 102 registros encadenados, sin faltantes ni alteraciones.')
  })

  it('lista cada problema con su registro', () => {
    const html = renderizar(
      <ResultadoVerificacion
        resultado={{
          integra: false,
          registros: 101,
          problemas: [
            { secuencia: 37, descripcion: 'El registro fue alterado: su contenido no coincide con su huella.' },
            { secuencia: 51, descripcion: 'Faltan los registros 50 a 50.' },
          ],
        }}
      />,
    )

    expect(html).toContain('La bitácora no está íntegra.')
    expect(html).toContain('Registro 37: El registro fue alterado')
    expect(html).toContain('Registro 51: Faltan los registros 50 a 50.')
  })

  it('la opción Bitácora del menú lleva a esta pantalla', () => {
    expect(opcionesPara(['BITACORA_CONSULTAR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Bitácora', ruta: '/bitacora' }),
    ])
  })

  it('la verificación es un GET a la API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('{"integra":true,"registros":3,"problemas":[]}', { status: 200 }),
    ))

    await expect(verificarBitacora()).resolves.toMatchObject({ integra: true, registros: 3 })
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/bitacora/verificacion')
  })
})
