import type { MatrizPermisos, RolConfigurable } from '../../api/permisos'

/** Lo que tiene cada rol configurable, como conjuntos de códigos. */
export type Asignacion = Record<RolConfigurable, Set<string>>

export const asignacionDe = (matriz: MatrizPermisos): Asignacion => {
  const permisos = matriz.modulos.flatMap((modulo) => modulo.permisos)
  return {
    SUPERVISOR: new Set(permisos.filter((p) => p.supervisor).map((p) => p.codigo)),
    CAJERO: new Set(permisos.filter((p) => p.cajero).map((p) => p.codigo)),
  }
}

const iguales = (a: Set<string>, b: Set<string>) => a.size === b.size && [...a].every((codigo) => b.has(codigo))

/** Los roles cuya asignación cambió respecto de la guardada. */
export const rolesCambiados = (guardada: Asignacion, editada: Asignacion) =>
  (Object.keys(guardada) as RolConfigurable[]).filter((rol) => !iguales(guardada[rol], editada[rol]))

export const AVISO_SIGUIENTE_INICIO =
  'Los cambios se aplican desde el siguiente inicio de sesión de cada usuario; las sesiones abiertas conservan sus permisos.'
