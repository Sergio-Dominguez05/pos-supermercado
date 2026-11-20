import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  certificarContingencia,
  CLAVE_CONTINGENCIA,
  CLAVE_ESTADO_CONTINGENCIA,
  obtenerContingencia,
  obtenerEstadoContingencia,
  type ResumenContingencia,
} from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { useSesion } from '../login/sesion'
import { avisoContingenciaSupervisor, avisoContingenciaVenta } from './textos'

const fecha = (iso: string) => new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })
/** El aviso se actualiza solo: el proceso automático certifica cada minuto (HU-08.08). */
const CADA_MINUTO = 60_000

/** HU-08.07 crit. 3: el aviso permanente de la pantalla de venta mientras haya comprobantes en contingencia. */
export function AvisoContingenciaVenta() {
  const { data } = useQuery({
    queryKey: CLAVE_ESTADO_CONTINGENCIA,
    queryFn: obtenerEstadoContingencia,
    refetchInterval: CADA_MINUTO,
  })
  if (!data?.enContingencia) return null
  return (
    <p className="pendiente" role="status">
      {avisoContingenciaVenta(data.pendientes)}
    </p>
  )
}

/** HU-08.09 crit. 1 y 2: el aviso en Inicio para quien gestiona facturas; destacado cerca del plazo. */
export function AvisoContingenciaSupervisor() {
  const { data } = useQuery({ queryKey: CLAVE_CONTINGENCIA, queryFn: obtenerContingencia, refetchInterval: CADA_MINUTO })
  if (!data || data.pendientes === 0) return null
  return (
    <p className={data.alerta ? 'tarjeta error' : 'tarjeta pendiente'} role={data.alerta ? 'alert' : 'status'}>
      {avisoContingenciaSupervisor(data.pendientes, data.diasMasAntigua, data.venceMasAntigua)}{' '}
      <Link to="/facturas/contingencia">Ver comprobantes en contingencia</Link>
    </p>
  )
}

/** HU-08.08 y HU-08.09: los comprobantes pendientes de certificar, para el Supervisor y el Administrador. */
export function PaginaContingencia() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Comprobantes en contingencia</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Contingencia
      </p>
      {usuario.permisos.includes('FACTURA_ANULAR') ? (
        <CargaContingencia />
      ) : (
        <p className="tarjeta error">No tiene permiso para gestionar la contingencia.</p>
      )}
    </main>
  )
}

function CargaContingencia() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_CONTINGENCIA,
    queryFn: obtenerContingencia,
    refetchInterval: CADA_MINUTO,
  })
  if (isPending) return <p className="tarjeta">Cargando comprobantes…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <ResumenDeContingencia resumen={data} />
}

export function ResumenDeContingencia({ resumen }: { resumen: ResumenContingencia }) {
  const clienteConsultas = useQueryClient()
  const certificacion = useMutation({
    mutationFn: () => certificarContingencia(),
    onSettled: () => clienteConsultas.invalidateQueries({ queryKey: CLAVE_CONTINGENCIA }),
  })

  return (
    <>
      <section className="tarjeta">
        {resumen.pendientes === 0 ? (
          <p>No hay comprobantes pendientes de certificar.</p>
        ) : (
          <p className={resumen.alerta ? 'error' : undefined} role={resumen.alerta ? 'alert' : undefined}>
            {avisoContingenciaSupervisor(resumen.pendientes, resumen.diasMasAntigua, resumen.venceMasAntigua)}
          </p>
        )}
        <p>
          La SAT da {resumen.diasMaximos} días para certificarlos; la alerta empieza el día {resumen.diaDeAlerta}. El
          sistema los certifica solo cada minuto, en orden de emisión.
        </p>
        <button type="button" disabled={certificacion.isPending} onClick={() => certificacion.mutate()}>
          {certificacion.isPending ? 'Certificando…' : 'Certificar pendientes ahora'}
        </button>
        {certificacion.data && <p role="status">{certificacion.data.mensaje}</p>}
        {certificacion.error && (
          <p className="error" role="alert">
            {certificacion.error.message}
          </p>
        )}
      </section>
      {resumen.lista.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Venta</th>
                <th>Número de acceso</th>
                <th>Emitida</th>
                <th>Días</th>
                <th>Total</th>
                <th>Último intento</th>
              </tr>
            </thead>
            <tbody>
              {resumen.lista.map((pendiente) => (
                <tr key={pendiente.ventaId}>
                  <td>
                    N.º {pendiente.ventaNumero} · {pendiente.cajaNombre}
                  </td>
                  <td>{pendiente.numeroAcceso ?? '—'}</td>
                  <td>{fecha(pendiente.emitidaEn)}</td>
                  <td>
                    {pendiente.alerta ? <strong className="error">{pendiente.dias}</strong> : pendiente.dias}
                  </td>
                  <td>{formatoQuetzales(pendiente.total)}</td>
                  <td>
                    {pendiente.intentos} {pendiente.intentos === 1 ? 'intento' : 'intentos'}
                    {pendiente.ultimoError && <small> · {pendiente.ultimoError}</small>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
