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

  it('el administrador ve todas las opciones', () => {
    expect(opcionesPara(OPCIONES.map((o) => o.permiso))).toEqual(OPCIONES)
  })

  it('sin permisos no hay opciones', () => {
    expect(opcionesPara([])).toEqual([])
  })
})
