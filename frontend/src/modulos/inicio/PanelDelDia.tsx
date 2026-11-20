import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  CLAVE_PANEL_DEL_DIA,
  INTERVALO_PANEL_MS,
  obtenerPanelDelDia,
  type PanelDelDia as Panel,
} from '../../api/reportes'
import { formatoQuetzales } from '../catalogo/textos'
import { fechaHora } from '../reportes/filtros'
import { alertasDelPanel } from './textos'

/**
 * HU-10.12 Panel resumen del día, al entrar al sistema (para quien consulta reportes).
 * Crit. 3: se vuelve a pedir cada minuto sin recargar la pantalla.
 */
export function PanelDelDia() {
  const { data, error } = useQuery({
    queryKey: CLAVE_PANEL_DEL_DIA,
    queryFn: obtenerPanelDelDia,
    refetchInterval: INTERVALO_PANEL_MS,
    refetchIntervalInBackground: false,
  })
  if (error) return <p className="tarjeta error">No se pudo cargar el resumen del día: {error.message}</p>
  if (!data) return <p className="tarjeta">Cargando el resumen del día…</p>
  return <ResumenDelDia panel={data} />
}

/** Crit. 1 y 2: las cifras de hoy, las cajas abiertas, las tarjetas y las alertas con un enlace a dónde atenderlas. */
export function ResumenDelDia({ panel }: { panel: Panel }) {
  const alertas = alertasDelPanel(panel.alertas)
  return (
    <section className="tarjeta panel-del-dia" aria-label="Resumen del día">
      <h2>Resumen de hoy</h2>
      {alertas.length > 0 && (
        <ul className="alertas" aria-label="Alertas">
          {alertas.map((alerta) => (
            <li key={alerta.ruta} className="error" role="alert">
              {alerta.texto} <Link to={alerta.ruta}>Revisar</Link>
            </li>
          ))}
        </ul>
      )}
      <dl className="cifras">
        <div>
          <dt>Total vendido</dt>
          <dd>{formatoQuetzales(panel.totalVendido)}</dd>
        </div>
        <div>
          <dt>Ventas</dt>
          <dd>{panel.cantidadVentas}</dd>
        </div>
        <div>
          <dt>Ticket promedio</dt>
          <dd>{formatoQuetzales(panel.ticketPromedio)}</dd>
        </div>
        <div>
          <dt>Cajas abiertas</dt>
          <dd>{panel.cajasAbiertas.length}</dd>
        </div>
        {panel.ventasPorTarjeta.map((tarjeta) => (
          <div key={tarjeta.tipo}>
            <dt>{tarjeta.etiqueta}</dt>
            <dd>
              {formatoQuetzales(tarjeta.monto)} <small>({tarjeta.transacciones})</small>
            </dd>
          </div>
        ))}
      </dl>
      {panel.cajasAbiertas.length > 0 && (
        <ul className="cajas-abiertas" aria-label="Cajas abiertas">
          {panel.cajasAbiertas.map((caja) => (
            <li key={caja.turnoId}>
              {caja.caja} · {caja.cajero} · desde {fechaHora(caja.abiertaDesde)}
            </li>
          ))}
        </ul>
      )}
      <p>
        <small>
          Ventas sin las facturas anuladas. Actualizado el {fechaHora(panel.actualizadoEn)}; se actualiza solo cada minuto.
        </small>
      </p>
    </section>
  )
}
