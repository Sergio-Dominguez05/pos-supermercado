import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CLAVE_VENTA_EN_CURSO, CLAVE_VENTAS_SUSPENDIDAS, retomarVenta, type Venta } from '../../api/ventas'
import { avisoSonoro } from '../catalogo/sonido'
import { descripcionSuspendida } from './textos'

/**
 * HU-06.13: las ventas en espera de la caja (crit. 1 y 3: solo las de la caja propia, con su hora, artículos y
 * total), cada una con su botón para retomarla (crit. 2). Se abre con F8; el primer botón recibe el foco, así se
 * recorre con Tab y se elige con Enter. Escape cierra la lista. Al retomar, la venta se carga en curso con sus líneas
 * y sus precios (crit. 4) y `onRetomada` la anuncia.
 */
export function VentasSuspendidas({
  suspendidas,
  onCerrar,
  onRetomada,
}: {
  suspendidas: Venta[]
  onCerrar: () => void
  onRetomada: (venta: Venta) => void
}) {
  const clienteConsultas = useQueryClient()
  const retomada = useMutation({
    mutationFn: (ventaId: number) => retomarVenta(ventaId),
    onSuccess: (venta) => {
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
      onRetomada(venta)
      return clienteConsultas.invalidateQueries({ queryKey: CLAVE_VENTAS_SUSPENDIDAS })
    },
    onError: () => avisoSonoro(),
  })

  return (
    <section
      className="tarjeta ventas-suspendidas"
      aria-label="Ventas en espera"
      onKeyDown={(evento) => {
        if (evento.key === 'Escape') onCerrar()
      }}
    >
      <h2>Ventas en espera</h2>
      {suspendidas.length === 0 ? (
        <p>No hay ventas en espera en esta caja.</p>
      ) : (
        <ul className="lista-suspendidas">
          {suspendidas.map((venta, posicion) => (
            <li key={venta.id}>
              <span>{descripcionSuspendida(venta)}</span>
              <button
                type="button"
                autoFocus={posicion === 0}
                disabled={retomada.isPending}
                onClick={() => retomada.mutate(venta.id)}
              >
                Retomar
              </button>
            </li>
          ))}
        </ul>
      )}
      {retomada.error && (
        <p className="error" role="alert">
          {retomada.error.message}
        </p>
      )}
      <button type="button" onClick={onCerrar}>
        Cerrar <kbd>Esc</kbd>
      </button>
    </section>
  )
}
