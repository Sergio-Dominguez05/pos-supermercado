import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { buscarClientePorNit, type BusquedaNit, type Cliente } from '../../api/clientes'
import { reenviarFacturaDesdeCaja } from '../../api/facturas'
import type { FacturaDeVenta } from '../../api/pagos'
import { RegistroCliente } from '../clientes/ClienteDeLaVenta'
import { nitValido } from '../clientes/nit'
import { avisoBusqueda, NIT_CLIENTE_INVALIDO } from '../clientes/textos'
import { avisoFactura } from '../venta/cobro'
import { AVISO_SUPERVISOR } from './textos'

/**
 * HU-08.06: la factura de la venta recién cobrada que rechazó el certificador.
 * - Crit. 1: el motivo en lenguaje comprensible.
 * - Crit. 2: si el error es del cliente, el cajero busca otro NIT (o elige CF) y reenvía, sin volver a cobrar.
 * - Crit. 3: si no se corrige en caja, la venta queda pendiente de facturar en la lista del supervisor.
 */
export function CorregirFactura({
  ventaId,
  numero,
  factura,
  onTerminado,
}: {
  ventaId: number
  numero: number
  factura: FacturaDeVenta
  onTerminado: (resultado: FacturaDeVenta) => void
}) {
  const [nit, setNit] = useState('')
  const [problema, setProblema] = useState<string>()
  const [busqueda, setBusqueda] = useState<BusquedaNit>()
  const reenvio = useMutation({
    mutationFn: (cliente: Cliente | null) => reenviarFacturaDesdeCaja(ventaId, cliente?.id ?? null),
    onSuccess: onTerminado,
  })
  const consulta = useMutation({
    mutationFn: () => buscarClientePorNit(nit),
    onMutate: () => setBusqueda(undefined),
    onSuccess: setBusqueda,
  })

  return (
    <section className="tarjeta" aria-label="Factura rechazada">
      <h3>Factura de la venta N.º {numero} rechazada</h3>
      <p className="error" role="alert">
        {factura.motivo}
      </p>
      {!factura.corregible ? (
        <p className="pendiente">{AVISO_SUPERVISOR}</p>
      ) : (
        <>
          <form
            className="en-linea"
            onSubmit={(evento) => {
              evento.preventDefault()
              if (!nitValido(nit)) {
                setProblema(NIT_CLIENTE_INVALIDO)
                return
              }
              setProblema(undefined)
              consulta.mutate()
            }}
          >
            <label>
              NIT correcto
              <input
                name="nitCorregido"
                autoComplete="off"
                maxLength={20}
                value={nit}
                onChange={(evento) => setNit(evento.target.value)}
              />
            </label>
            <button type="submit" disabled={consulta.isPending || !nit.trim()}>
              {consulta.isPending ? 'Buscando…' : 'Buscar'}
            </button>
            <button type="button" disabled={reenvio.isPending} onClick={() => reenvio.mutate(null)}>
              Reenviar como CF
            </button>
          </form>
          {problema && (
            <p className="error" role="alert">
              {problema}
            </p>
          )}
          {busqueda && <p role="status">{avisoBusqueda(busqueda)}</p>}
          {busqueda?.cliente && (
            <button type="button" disabled={reenvio.isPending} onClick={() => reenvio.mutate(busqueda.cliente)}>
              {reenvio.isPending ? 'Reenviando…' : `Reenviar a ${busqueda.cliente.nombre}`}
            </button>
          )}
          {busqueda && !busqueda.cliente && (
            <RegistroCliente
              key={busqueda.nit}
              nit={busqueda.nit}
              onRegistrado={(cliente) => reenvio.mutate(cliente)}
              onConsumidorFinal={() => reenvio.mutate(null)}
            />
          )}
        </>
      )}
      {(consulta.error ?? reenvio.error) && (
        <p className="error" role="alert">
          {(consulta.error ?? reenvio.error)?.message}
        </p>
      )}
      {reenvio.data && <p role="status">{avisoFactura(reenvio.data)}</p>}
    </section>
  )
}
