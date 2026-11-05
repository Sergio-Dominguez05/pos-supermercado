import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import {
  activarCaja,
  CLAVE_CAJA_DEL_EQUIPO,
  CLAVE_CAJAS,
  cajaDelEquipo,
  desactivarCaja,
  listarCajas,
  vincularEquipo,
  type Caja,
} from '../../api/cajas'
import { useSesion } from '../login/sesion'
import { avisoEquipoVinculado, avisoEstadoCaja, confirmacionDesactivarCaja, confirmacionVincular } from './textos'

/** HU-12.05: cajas del supermercado, con el equipo físico vinculado a cada una. */
export function ListaCajas() {
  const { data: usuario } = useSesion()
  // Al volver de crear o editar una caja llega el aviso a mostrar.
  const avisoNavegacion = (useLocation().state as { aviso?: string } | null)?.aviso
  const [avisoAccion, setAvisoAccion] = useState<string>()
  const aviso = avisoAccion ?? avisoNavegacion
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Cajas</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Configuración · Cajas
      </p>
      {aviso && (
        <p className="tarjeta exito" role="status">
          {aviso}
        </p>
      )}
      {/* El servidor exige el permiso; esto solo evita mostrar una lista que no se podrá cargar. */}
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <TablaCajas onAviso={setAvisoAccion} />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function TablaCajas({ onAviso }: { onAviso: (aviso?: string) => void }) {
  const clienteConsultas = useQueryClient()
  const { data: cajas, error, isPending } = useQuery({ queryKey: CLAVE_CAJAS, queryFn: listarCajas })
  const { data: equipoActual } = useQuery({ queryKey: CLAVE_CAJA_DEL_EQUIPO, queryFn: cajaDelEquipo })

  const accion = useMutation({
    mutationFn: ({ caja, tipo }: { caja: Caja; tipo: 'activar' | 'desactivar' | 'vincular' }) =>
      tipo === 'activar' ? activarCaja(caja.id) : tipo === 'desactivar' ? desactivarCaja(caja.id) : vincularEquipo(caja.id),
    onMutate: () => onAviso(undefined),
    onSuccess: async (caja, { tipo }) => {
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_CAJAS })
      onAviso(tipo === 'vincular' ? avisoEquipoVinculado(caja) : avisoEstadoCaja(caja))
    },
  })

  const ejecutar = (caja: Caja, tipo: 'activar' | 'desactivar' | 'vincular') => {
    const pregunta =
      tipo === 'desactivar'
        ? confirmacionDesactivarCaja(caja)
        : tipo === 'vincular'
          ? confirmacionVincular(caja, equipoActual ?? null)
          : null
    if (pregunta && !window.confirm(pregunta)) return
    accion.mutate({ caja, tipo })
  }

  return (
    <section className="tarjeta">
      <div className="barra-filtros">
        <p className="pendiente" role="note">
          {equipoActual
            ? `Este equipo está vinculado a ${equipoActual.nombre}.`
            : 'Este equipo no está vinculado a ninguna caja.'}
        </p>
        <Link className="boton" to="/configuracion/cajas/nueva">
          Nueva caja
        </Link>
      </div>

      {(error ?? accion.error) && (
        <p className="error" role="alert">
          {(error ?? accion.error)?.message}
        </p>
      )}
      {isPending && <p>Cargando cajas…</p>}
      {cajas && cajas.length === 0 && <p className="pendiente">Todavía no hay cajas registradas.</p>}
      {cajas && cajas.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Caja</th>
                <th>Serie FEL</th>
                <th>Estado</th>
                <th>Equipo</th>
                <th>
                  <span className="solo-lector">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {cajas.map((caja) => (
                <tr key={caja.id}>
                  <td>{caja.nombre}</td>
                  <td>{caja.serieFel ?? '—'}</td>
                  <td>
                    <span className={caja.activo ? 'estado estado-activo' : 'estado estado-inactivo'}>
                      {caja.activo ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td>
                    {equipoActual?.id === caja.id ? 'Este equipo' : caja.equipoVinculado ? 'Vinculado' : 'Sin vincular'}
                  </td>
                  <td className="acciones">
                    <Link to={`/configuracion/cajas/${caja.id}/editar`} aria-label={`Editar ${caja.nombre}`}>
                      Editar
                    </Link>
                    {caja.activo && equipoActual?.id !== caja.id && (
                      <button
                        type="button"
                        className="boton-texto"
                        disabled={accion.isPending}
                        aria-label={`Vincular este equipo a ${caja.nombre}`}
                        onClick={() => ejecutar(caja, 'vincular')}
                      >
                        Vincular este equipo
                      </button>
                    )}
                    <button
                      type="button"
                      className={caja.activo ? 'boton-texto peligro' : 'boton-texto'}
                      disabled={accion.isPending}
                      aria-label={`${caja.activo ? 'Desactivar' : 'Activar'} ${caja.nombre}`}
                      onClick={() => ejecutar(caja, caja.activo ? 'desactivar' : 'activar')}
                    >
                      {caja.activo ? 'Desactivar' : 'Activar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
