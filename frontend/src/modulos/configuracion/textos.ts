import type { Caja } from '../../api/cajas'

// Textos de la gestión de cajas (HU-12.05), separados de los componentes para probarlos sin renderizar.

export const avisoCajaGuardada = (caja: Caja, nueva: boolean) =>
  `Se ${nueva ? 'creó' : 'actualizó'} la caja ${caja.nombre}.`

export const avisoEstadoCaja = (caja: Caja) => `Se ${caja.activo ? 'activó' : 'desactivó'} la caja ${caja.nombre}.`

export const avisoEquipoVinculado = (caja: Caja) =>
  `Este equipo quedó vinculado a ${caja.nombre}. Quien inicie sesión aquí trabajará en esa caja.`

export const confirmacionDesactivarCaja = (caja: Caja) =>
  `¿Desactivar ${caja.nombre}? No se podrá usar para vender hasta que la vuelva a activar.`

export const confirmacionVincular = (caja: Caja, equipoActual: Caja | null) =>
  `¿Vincular este equipo a ${caja.nombre}?` +
  (caja.equipoVinculado ? ' El equipo que estaba vinculado a esa caja dejará de estarlo.' : '') +
  (equipoActual && equipoActual.id !== caja.id ? ` Este equipo dejará de ser ${equipoActual.nombre}.` : '')
