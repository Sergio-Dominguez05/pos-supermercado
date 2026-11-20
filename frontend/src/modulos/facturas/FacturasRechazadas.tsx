import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  CLAVE_FACTURAS_RECHAZADAS,
  listarFacturasRechazadas,
  reenviarFacturaRechazada,
  type FacturaRechazada,
} from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { nitConGuion } from '../clientes/textos'
import { useSesion } from '../login/sesion'
import { avisoFactura } from '../venta/cobro'
import { TIPOS_DE_RECHAZO } from './textos'

const fecha = (iso: string) => new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })

/** HU-08.06 crit. 3: las ventas pendientes de facturar, para el Supervisor y el Administrador. */
export function PaginaFacturasRechazadas() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Facturas rechazadas</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Facturas rechazadas
      </p>
      {usuario.permisos.includes('FACTURA_ANULAR') ? (
        <CargaRechazadas />
      ) : (
        <p className="tarjeta error">No tiene permiso para gestionar las facturas rechazadas.</p>
      )}
    </main>
  )
}

function CargaRechazadas() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_FACTURAS_RECHAZADAS,
    queryFn: listarFacturasRechazadas,
  })

  if (isPending) return <p className="tarjeta">Cargando facturas rechazadas…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <ListaRechazadas rechazadas={data} />
}

/**
 * Cada venta con su motivo comprensible (crit. 1) y la respuesta completa del certificador (crit. 4). Cuando se corrige
 * la causa (por ejemplo, la configuración del emisor), "Reenviar" la certifica de nuevo con el mismo cliente.
 */
export function ListaRechazadas({ rechazadas }: { rechazadas: FacturaRechazada[] }) {
  if (rechazadas.length === 0) {
    return <p className="tarjeta">No hay ventas pendientes de facturar.</p>
  }
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>Venta</th>
            <th>Fecha</th>
            <th>Caja y cajero</th>
            <th>Cliente</th>
            <th>Total</th>
            <th>Motivo</th>
            <th>
              <span className="solo-lector">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rechazadas.map((rechazada) => (
            <FilaRechazada key={rechazada.ventaId} rechazada={rechazada} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FilaRechazada({ rechazada }: { rechazada: FacturaRechazada }) {
  const clienteConsultas = useQueryClient()
  const reenvio = useMutation({
    mutationFn: () => reenviarFacturaRechazada(rechazada.ventaId),
    onSettled: () => clienteConsultas.invalidateQueries({ queryKey: CLAVE_FACTURAS_RECHAZADAS }),
  })

  return (
    <tr>
      <td>N.º {rechazada.ventaNumero}</td>
      <td>{fecha(rechazada.emitidaEn)}</td>
      <td>
        {rechazada.cajaNombre} · {rechazada.cajero}
      </td>
      <td>
        {rechazada.receptorId === 'CF' ? 'CF' : `NIT ${nitConGuion(rechazada.receptorId)}`} ·{' '}
        {rechazada.receptorNombre}
      </td>
      <td>{formatoQuetzales(rechazada.total)}</td>
      <td>
        <strong>{TIPOS_DE_RECHAZO[rechazada.tipo]}:</strong> {rechazada.motivo}
        <details>
          <summary>Respuesta del certificador</summary>
          <p>
            {rechazada.codigo} · {rechazada.mensajeCertificador}
          </p>
          {rechazada.respuesta && <pre>{rechazada.respuesta}</pre>}
        </details>
      </td>
      <td className="acciones">
        <button
          type="button"
          disabled={reenvio.isPending}
          onClick={() => reenvio.mutate()}
          aria-label={`Reenviar la factura de la venta ${rechazada.ventaNumero}`}
        >
          {reenvio.isPending ? 'Reenviando…' : 'Reenviar'}
        </button>
        {reenvio.data && <p role="status">{avisoFactura(reenvio.data)}</p>}
        {reenvio.error && (
          <p className="error" role="alert">
            {reenvio.error.message}
          </p>
        )}
      </td>
    </tr>
  )
}
