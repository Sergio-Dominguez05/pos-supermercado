import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CLAVE_VENTA_EN_CURSO, suspenderVenta, type Venta } from '../../api/ventas'
import { avisoSonoro } from '../catalogo/sonido'

/**
 * HU-06.12: deja la venta en espera para atender a otro cliente. El servidor la guarda con todas sus líneas
 * (crit. 1) y la pantalla inicia la siguiente venta; `onSuspendida` avisa cuál quedó en espera. No pide confirmación:
 * suspender no pierde nada. Una venta sin productos no tiene nada que suspender.
 */
export function SuspenderVenta({ venta, onSuspendida }: { venta: Venta; onSuspendida?: (venta: Venta) => void }) {
  const clienteConsultas = useQueryClient()
  const suspension = useMutation({
    mutationFn: () => suspenderVenta(),
    onSuccess: (suspendida) => {
      onSuspendida?.(suspendida)
      return clienteConsultas.invalidateQueries({ queryKey: CLAVE_VENTA_EN_CURSO })
    },
    onError: () => avisoSonoro(),
  })
  if (venta.lineas.length === 0) return null

  return (
    <>
      <button
        type="button"
        data-control="suspender-venta"
        disabled={suspension.isPending}
        onClick={() => suspension.mutate()}
      >
        {suspension.isPending ? 'Suspendiendo…' : 'Suspender venta'} <kbd>F7</kbd>
      </button>
      {suspension.error && (
        <p className="error" role="alert">
          {suspension.error.message}
        </p>
      )}
    </>
  )
}
