import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buscarEnBitacora,
  obtenerDetalleEvento,
  rutaBitacora,
  type DetalleEvento,
  type PaginaBitacora,
  type RegistroBitacora,
} from '../../api/bitacora'
import { FiltrosDeBitacora, TablaBitacora, VistaEvento } from './Bitacora'
import { filasDeCambios, filtrosDeHoy, rotuloBitacora, rutaDocumento } from './consulta'

const REGISTRO: RegistroBitacora = {
  id: 501,
  secuencia: 1200,
  ocurridoEn: '2026-11-20T21:15:30Z',
  usuario: 'Ana Admin (admin)',
  rol: 'ADMIN',
  caja: 'Caja 1',
  modulo: 'catalogo',
  accion: 'PRECIO_CAMBIADO',
  exitoso: true,
  descripcion: "Producto 'Arroz 1 lb' (7401): precio cambiado de Q8.00 a Q8.50",
}

const PAGINA: PaginaBitacora = {
  registros: [REGISTRO, { ...REGISTRO, id: 500, exitoso: false }],
  pagina: 0,
  tamanoPagina: 50,
  total: 2,
}

const DETALLE: DetalleEvento = {
  registro: REGISTRO,
  entidad: 'producto',
  entidadId: 9,
  cambios: { precio: { antes: '8.00', despues: '8.50' } },
  documento: { tipo: 'PRODUCTO', id: 9, etiqueta: 'Arroz 1 lb' },
}

const renderizar = (contenido: ReactNode) =>
  renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('consultar la bitácora (HU-11.06)', () => {
  it('lista fecha y hora, usuario, caja, módulo, acción y resultado, con enlace al detalle (crit. 1 y 2)', () => {
    const html = renderizar(<TablaBitacora pagina={PAGINA} onPagina={() => {}} />)

    for (const columna of ['Fecha y hora', 'Usuario', 'Caja', 'Módulo', 'Acción', 'Resultado'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('href="/bitacora/501"')
    expect(html).toContain('<td>Ana Admin (admin)</td><td>Caja 1</td><td>catalogo</td>')
    expect(html).toContain('<td class="exito">Exitoso</td>')
    expect(html).toContain('<td class="error">Fallido</td>')
    expect(html).toContain('Mostrando 1–2 de 2 registros')
    expect(rotuloBitacora(0, 50, 0, 0)).toBe('No hay registros con estos filtros.')
  })

  it('abre con los registros de hoy en hora de Guatemala (decisión de Sergio)', () => {
    // A las 23:30 de Guatemala ya es otro día en UTC: cuenta el de Guatemala.
    expect(filtrosDeHoy(new Date('2026-11-20T23:30:00-06:00'))).toMatchObject({
      desde: '2026-11-20',
      hasta: '2026-11-20',
    })
  })
})

describe('filtrar la bitácora (HU-11.07)', () => {
  it('filtra por fechas, usuario, caja, módulo, acción, resultado y documento, combinados (crit. 1 a 3)', () => {
    const html = renderizar(
      <FiltrosDeBitacora
        iniciales={filtrosDeHoy(new Date('2026-11-20T12:00:00-06:00'))}
        opciones={{
          usuarios: [{ id: 1, etiqueta: 'Ana Admin (admin)' }],
          cajas: [{ id: 2, etiqueta: 'Caja 2' }],
          modulos: ['catalogo', 'ventas'],
          acciones: ['PRECIO_CAMBIADO'],
        }}
        onBuscar={() => {}}
      />,
    )
    for (const nombre of ['desde', 'hasta', 'usuarioId', 'cajaId', 'modulo', 'accion', 'exitoso', 'documento'])
      expect(html).toContain(`name="${nombre}"`)
    expect(html).toContain('<option value="1">Ana Admin (admin)</option>')
    expect(html).toContain('<option value="false">Fallido</option>')
    expect(html).toContain('placeholder="Venta, factura (A1-77) o cierre"')

    const filtros = {
      ...filtrosDeHoy(new Date('2026-11-20T12:00:00-06:00')),
      accion: 'PRECIO_CAMBIADO',
      exitoso: 'false' as const,
      documento: ' A1-77 ',
    }
    expect(rutaBitacora(filtros, 2)).toBe(
      '/api/bitacora?desde=2026-11-20&hasta=2026-11-20&accion=PRECIO_CAMBIADO&exitoso=false&documento=A1-77&pagina=2',
    )
  })

  it('consulta con la ruta del servidor', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => new Response('{}', { status: 200 })),
    )
    await buscarEnBitacora(filtrosDeHoy(new Date('2026-11-20T12:00:00-06:00')), 0)
    await obtenerDetalleEvento(501)
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/bitacora?desde=2026-11-20&hasta=2026-11-20')
    expect(vi.mocked(fetch).mock.calls[1][0]).toBe('/api/bitacora/501')
  })
})

describe('detalle de un evento (HU-11.08)', () => {
  it('muestra todo lo guardado con los valores anterior y nuevo (crit. 1)', () => {
    const html = renderizar(<VistaEvento detalle={DETALLE} permisos={['BITACORA_CONSULTAR']} />)

    expect(html).toContain('<h2>PRECIO_CAMBIADO · Exitoso</h2>')
    expect(html).toContain('<dt>Registro N.º</dt><dd>1200</dd>')
    expect(html).toContain('<td>precio</td><td>8.00</td><td>8.50</td>')
    expect(filasDeCambios({ filtros: { accion: 'X' }, motivo: 'Sin conexión' })).toEqual([
      { campo: 'filtros', valor: '{"accion":"X"}' },
      { campo: 'motivo', valor: 'Sin conexión' },
    ])
    expect(filasDeCambios(null)).toEqual([])
  })

  it('abre el documento relacionado solo con el permiso de su pantalla (crit. 2)', () => {
    expect(
      renderizar(<VistaEvento detalle={DETALLE} permisos={['BITACORA_CONSULTAR', 'PRODUCTO_CONSULTAR']} />),
    ).toContain('href="/catalogo/9"')
    const sinPermiso = renderizar(<VistaEvento detalle={DETALLE} permisos={['BITACORA_CONSULTAR']} />)
    expect(sinPermiso).toContain('<dd>Arroz 1 lb</dd>')
    expect(sinPermiso).not.toContain('href="/catalogo/9"')

    expect(rutaDocumento({ tipo: 'FACTURA', id: 7, etiqueta: '' }, ['FACTURA_ANULAR'])).toBe('/facturas/7')
    expect(rutaDocumento({ tipo: 'CIERRE', id: 12, etiqueta: '' }, ['CAJA_VER_CIERRES'])).toBe('/caja/cierres/12')
    expect(rutaDocumento({ tipo: 'USUARIO', id: 3, etiqueta: '' }, ['USUARIO_ADMINISTRAR'])).toBe('/usuarios/3/editar')
    expect(rutaDocumento({ tipo: 'FACTURA', id: 7, etiqueta: '' }, ['BITACORA_CONSULTAR'])).toBeNull()
  })
})
