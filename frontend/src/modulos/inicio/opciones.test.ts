import { describe, expect, it } from 'vitest'
import { OPCIONES, opcionesPara } from './opciones'

const etiquetas = (permisos: string[]) => opcionesPara(permisos).map((o) => o.etiqueta)

describe('opciones del menú por rol', () => {
  it('el supervisor ve reportes y bitácora, pero no usuarios ni configuración', () => {
    const supervisor = [
      'VENTA_REGISTRAR', 'CAJA_OPERAR_PROPIA', 'CLIENTE_GESTIONAR', 'FACTURA_REIMPRIMIR',
      'PRODUCTO_CONSULTAR', 'REPORTE_CONSULTAR', 'BITACORA_CONSULTAR', 'FACTURA_ANULAR',
    ]
    expect(etiquetas(supervisor)).toEqual(expect.arrayContaining(['Venta', 'Reportes', 'Bitácora']))
    expect(etiquetas(supervisor)).not.toContain('Usuarios')
    expect(etiquetas(supervisor)).not.toContain('Configuración')
  })

  it('el cajero solo ve las opciones de su rol; las demás no aparecen (HU-01.11 crit. 1)', () => {
    const cajero = [
      'VENTA_REGISTRAR', 'VENTA_SUSPENDER_CANCELAR', 'PAGO_REGISTRAR', 'CLIENTE_GESTIONAR',
      'FACTURA_REIMPRIMIR', 'CAJA_OPERAR_PROPIA', 'PRODUCTO_CONSULTAR',
    ]
    expect(etiquetas(cajero)).toEqual(['Venta', 'Caja', 'Clientes', 'Facturas', 'Catálogo'])
  })

  it('el administrador ve todas las opciones', () => {
    expect(opcionesPara(OPCIONES.map((o) => o.permiso))).toEqual(OPCIONES)
  })

  it('la opción Usuarios lleva a la lista de usuarios (HU-01.06)', () => {
    expect(opcionesPara(['USUARIO_ADMINISTRAR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Usuarios', ruta: '/usuarios' }),
    ])
  })

  it('la opción Configuración lleva a las cajas (HU-12.05)', () => {
    expect(opcionesPara(['CONFIGURACION_MODIFICAR'])).toEqual([
      expect.objectContaining({ etiqueta: 'Configuración', ruta: '/configuracion/cajas' }),
    ])
  })

  it('sin permisos no hay opciones', () => {
    expect(opcionesPara([])).toEqual([])
  })
})
