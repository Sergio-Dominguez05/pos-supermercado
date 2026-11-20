import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UsuarioSesion } from '../../api/autenticacion'
import {
  FILTROS_HISTORIAL_VACIOS,
  rutaHistorial,
  type DetalleFactura,
  type HistorialFacturas,
} from '../../api/facturas'
import { CLAVE_SESION } from '../login/sesion'
import { PaginaDetalleFactura, VistaDetalleFactura } from './DetalleFactura'
import { FiltrosDelHistorial, PaginaHistorialFacturas, TablaHistorial } from './HistorialFacturas'
import { numeroDeFactura, rotuloPagina } from './textos'

const SUPERVISOR: Partial<UsuarioSesion> = { rol: 'SUPERVISOR', permisos: ['FACTURA_ANULAR', 'FACTURA_REIMPRIMIR'] }
const CAJERO: Partial<UsuarioSesion> = { rol: 'CAJERO', permisos: ['FACTURA_REIMPRIMIR'] }

const HISTORIAL: HistorialFacturas = {
  facturas: [
    {
      facturaId: 9,
      ventaNumero: 41,
      emitidaEn: '2026-11-16T16:00:00Z',
      serie: 'A1',
      numero: '77',
      autorizacion: '6f1c2a10-0000-4000-8000-000000000001',
      numeroAcceso: null,
      receptorId: '1234567',
      receptorNombre: 'Comercial Ñandú',
      total: 112,
      estado: 'CERTIFICADA',
      caja: 'Caja 1',
      cajero: 'Lucía Méndez (lucia.mendez)',
    },
    {
      facturaId: 10,
      ventaNumero: 42,
      emitidaEn: '2026-11-16T17:00:00Z',
      serie: null,
      numero: null,
      autorizacion: null,
      numeroAcceso: 100000007,
      receptorId: 'CF',
      receptorNombre: 'Consumidor Final',
      total: 22.4,
      estado: 'CONTINGENCIA',
      caja: 'Caja 1',
      cajero: 'Lucía Méndez (lucia.mendez)',
    },
  ],
  pagina: 0,
  tamanoPagina: 50,
  total: 2,
}

const DETALLE: DetalleFactura = {
  facturaId: 9,
  ventaId: 31,
  ventaNumero: 41,
  estado: 'CERTIFICADA',
  caja: 'Caja 1',
  cajero: 'Lucía Méndez (lucia.mendez)',
  turnoAbierto: true,
  emitidaEn: '2026-11-16T16:00:00Z',
  emisor: {
    nombreComercial: 'Súper del Valle',
    razonSocial: 'Supermercados del Valle, S.A.',
    nit: '576937K',
    direccionFiscal: 'Zona 1, Guatemala',
    establecimiento: 'Matriz',
    codigoEstablecimiento: '1',
    direccionEstablecimiento: 'Zona 1',
  },
  receptor: { tipo: 'NIT', id: '1234567', nombre: 'Comercial Ñandú', direccion: null, correo: null },
  certificacion: {
    serie: 'A1',
    numero: '77',
    autorizacion: '6f1c2a10-0000-4000-8000-000000000001',
    certificadaEn: '2026-11-16T16:00:05Z',
    numeroAcceso: null,
  },
  lineas: [
    { descripcion: 'Arroz 1 lb', cantidad: 2, precioUnitario: 11.2, subtotal: 22.4 },
    { descripcion: 'Frijol 1 lb', cantidad: 1, precioUnitario: 11.2, subtotal: 11.2 },
  ],
  subtotal: 30,
  iva: 3.6,
  total: 33.6,
  pagos: [{ tipoTarjeta: 'DEBITO', autorizacion: 'AUT-1', ultimos4: '4242', monto: 33.6 }],
  historial: [
    { momento: '2026-11-16T16:00:00Z', tipo: 'EMITIDA', descripcion: 'Emitida con la venta N.º 41', usuario: null },
    { momento: '2026-11-16T16:00:05Z', tipo: 'CERTIFICADA', descripcion: 'Certificada ante la SAT', usuario: null },
    {
      momento: '2026-11-16T17:00:00Z',
      tipo: 'REIMPRESA',
      descripcion: 'Reimpresa en Caja 1',
      usuario: 'Ana Supervisora (ana.sup)',
    },
  ],
}

const renderizar = (contenido: ReactNode, sesion = SUPERVISOR) => {
  const cliente = new QueryClient()
  cliente.setQueryData(CLAVE_SESION, sesion)
  return renderToString(
    <QueryClientProvider client={cliente}>
      <MemoryRouter>{contenido}</MemoryRouter>
    </QueryClientProvider>,
  ).replaceAll('<!-- -->', '')
}

describe('historial de facturas (HU-08.13)', () => {
  it('solo el Supervisor y el Administrador lo consultan', () => {
    expect(renderizar(<PaginaHistorialFacturas />)).toContain('Filtrar facturas')
    expect(renderizar(<PaginaHistorialFacturas />, CAJERO)).toContain(
      'No tiene permiso para consultar el historial de facturas.',
    )
    expect(renderizar(<PaginaDetalleFactura />, CAJERO)).toContain(
      'No tiene permiso para consultar el detalle de las facturas.',
    )
  })

  it('muestra fecha, factura, autorización, cliente, total, estado y caja, con enlace al detalle (crit. 1)', () => {
    const html = renderizar(<TablaHistorial historial={HISTORIAL} onPagina={() => {}} />)

    for (const columna of ['Emitida', 'Factura', 'Autorización', 'Cliente', 'Total', 'Estado', 'Caja y cajero'])
      expect(html).toContain(`<th>${columna}</th>`)
    expect(html).toContain('<a href="/facturas/9" data-discover="true">A1-77</a><small> · venta N.º 41</small>')
    expect(html).toContain('6F1C2A10-0000-4000-8000-000000000001')
    expect(html).toContain('<td>123456-7 · Comercial Ñandú</td><td>Q112.00</td><td>Certificada</td>')
    expect(html).toContain('<a href="/facturas/10" data-discover="true">Acceso 100000007</a>')
    expect(html).toContain('<td>En contingencia</td>')
    expect(html).toContain('Mostrando 1–2 de 2 facturas')
  })

  it('ofrece los filtros y la búsqueda (crit. 2 y 3)', () => {
    const html = renderizar(
      <FiltrosDelHistorial
        opciones={{ cajas: [{ id: 1, etiqueta: 'Caja 1' }], cajeros: [{ id: 7, etiqueta: 'Lucía (lucia)' }] }}
        onBuscar={() => {}}
      />,
    )
    for (const filtro of ['buscar', 'cliente', 'desde', 'hasta', 'estado', 'cajaId', 'cajeroId'])
      expect(html).toContain(`name="${filtro}"`)
    expect(html).toContain('<option value="CONTINGENCIA">En contingencia</option>')
    expect(html).toContain('<option value="1">Caja 1</option>')
    expect(html).toContain('<option value="7">Lucía (lucia)</option>')
    expect(rutaHistorial(FILTROS_HISTORIAL_VACIOS, 0)).toBe('/api/facturas/historial')
    expect(
      rutaHistorial({ ...FILTROS_HISTORIAL_VACIOS, estado: 'ANULADA', cliente: ' ñandú ', desde: '2026-11-01' }, 2),
    ).toBe('/api/facturas/historial?desde=2026-11-01&estado=ANULADA&cliente=%C3%B1and%C3%BA&pagina=2')
  })

  it('pagina de cincuenta en cincuenta', () => {
    const muchas = { ...HISTORIAL, total: 120, pagina: 1 }
    const html = renderizar(<TablaHistorial historial={muchas} onPagina={() => {}} />)
    expect(html).toContain('>Anteriores</button>')
    expect(html).toContain('>Siguientes</button>')
    expect(rotuloPagina(1, 50, 120, 50)).toBe('Mostrando 51–100 de 120 facturas')
    expect(rotuloPagina(0, 50, 0, 0)).toBe('No hay facturas con estos filtros.')
    expect(numeroDeFactura({ serie: null, numero: null, numeroAcceso: null })).toBe('—')
  })
})

describe('detalle de una factura (HU-08.14)', () => {
  it('muestra emisor, cliente, productos, totales, pagos y certificación (crit. 1)', () => {
    const html = renderizar(<VistaDetalleFactura factura={DETALLE} permisos={SUPERVISOR.permisos ?? []} />)

    expect(html).toContain('<h2>A1-77 · Certificada</h2>')
    expect(html).toContain('<strong>Súper del Valle</strong>')
    expect(html).toContain('Supermercados del Valle, S.A. · NIT 576937-K')
    expect(html).toContain('Establecimiento 1 · Matriz')
    expect(html).toContain('<strong>Comercial Ñandú</strong><br/>NIT 123456-7')
    expect(html).toContain('<td>Arroz 1 lb</td><td>2</td><td>Q11.20</td><td>Q22.40</td>')
    expect(html).toContain('Subtotal</th><td>Q30.00</td>')
    expect(html).toContain('IVA</th><td>Q3.60</td>')
    expect(html).toContain('TOTAL</th><td>Q33.60</td>')
    expect(html).toContain('Débito •••• 4242 · autorización AUT-1 · Q33.60')
    expect(html).toContain('6F1C2A10-0000-4000-8000-000000000001')
  })

  it('muestra el historial de la factura en orden (crit. 2)', () => {
    const html = renderizar(<VistaDetalleFactura factura={DETALLE} permisos={SUPERVISOR.permisos ?? []} />)

    const emitida = html.indexOf('Emitida con la venta N.º 41')
    const certificada = html.indexOf('Certificada ante la SAT')
    const reimpresa = html.indexOf('Reimpresa en Caja 1')
    expect(emitida).toBeGreaterThan(0)
    expect(certificada).toBeGreaterThan(emitida)
    expect(reimpresa).toBeGreaterThan(certificada)
    expect(html).toContain('Reimpresa en Caja 1<small> · Ana Supervisora (ana.sup)</small>')
  })

  it('ofrece reimprimir y anular según los permisos y el estado (crit. 3)', () => {
    const supervisor = renderizar(<VistaDetalleFactura factura={DETALLE} permisos={SUPERVISOR.permisos ?? []} />)
    expect(supervisor).toContain('>Reimprimir</button>')
    expect(supervisor).toContain('aria-label="Anular la factura 77"')

    const soloReimprime = renderizar(<VistaDetalleFactura factura={DETALLE} permisos={['FACTURA_REIMPRIMIR']} />)
    expect(soloReimprime).toContain('>Reimprimir</button>')
    expect(soloReimprime).not.toContain('Anular la factura')

    const turnoCerrado = renderizar(
      <VistaDetalleFactura factura={{ ...DETALLE, turnoAbierto: false }} permisos={SUPERVISOR.permisos ?? []} />,
    )
    expect(turnoCerrado).toContain('turno cerrado, no se puede anular')

    const anulada = renderizar(
      <VistaDetalleFactura factura={{ ...DETALLE, estado: 'ANULADA' }} permisos={SUPERVISOR.permisos ?? []} />,
    )
    expect(anulada).toContain('<h2>A1-77 · Anulada</h2>')
    expect(anulada).not.toContain('>Reimprimir</button>')
    expect(anulada).not.toContain('Anular la factura')
  })
})
