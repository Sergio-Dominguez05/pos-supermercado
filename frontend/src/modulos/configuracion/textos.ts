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

// Conexión con el certificador FEL (HU-08.01, HU-12.03 y HU-12.15).

/** HU-12.03 crit. 3: pasar de pruebas a producción se confirma explícitamente. */
export const CONFIRMACION_PRODUCCION =
  '¿Pasar al ambiente de PRODUCCIÓN? Desde ahora las facturas se certifican ante la SAT y tienen validez fiscal. ' +
  'Hágalo solo cuando las pruebas con el certificador estén terminadas.'

/** El tiempo límite se escribe en segundos; el servidor lo guarda en milisegundos (HU-12.03 crit. 4). */
export const segundosAMs = (segundos: string) => Math.round(Number(segundos) * 1000)

export const segundosValidos = (segundos: string) =>
  /^\d+$/.test(segundos.trim()) && Number(segundos) >= 1 && Number(segundos) <= 60

export const avisoConexionGuardada = (ambiente: 'PRUEBAS' | 'PRODUCCION') =>
  `Se guardó la conexión con el certificador, ambiente ${ambiente === 'PRODUCCION' ? 'de producción' : 'de pruebas'}.`

// Datos del emisor (HU-12.01).

/** Crit. 2: el NIT no pasó la validación de la SAT (HU-05.03). */
export const NIT_INVALIDO = 'El NIT no es válido: revise los dígitos y el verificador (puede terminar en K).'

export const AVISO_EMISOR_GUARDADO = 'Se guardaron los datos del emisor. Las próximas facturas los usarán.'
