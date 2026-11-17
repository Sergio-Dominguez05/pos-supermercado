import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CLAVE_VENTA_EN_CURSO, eliminarLinea, type LineaVenta } from '../../api/ventas'
import { avisoSonoro } from '../catalogo/sonido'
import { confirmacionQuitarLinea } from './textos'

/**
 * HU-06.07: quita una línea de la venta (crit. 1) sin autorización de un supervisor (crit. 2), después de confirmarlo
 * para no perder un producto por un clic equivocado. El servidor la registra en la bitácora (crit. 3) y devuelve los
 * totales recalculados, que la pantalla muestra al instante (crit. 4). Al terminar, `onTerminado` devuelve el foco al
 * campo del código.
 */
export function QuitarLinea({ linea, onTerminado }: { linea: LineaVenta; onTerminado?: () => void }) {
  const clienteConsultas = useQueryClient()
  const quitar = useMutation({
    mutationFn: () => eliminarLinea(linea.id),
    onSuccess: (venta) => {
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
      onTerminado?.()
    },
    onError: () => avisoSonoro(),
  })

  return (
    <>
      <button
        type="button"
        data-control="quitar"
        aria-label={`Quitar ${linea.descripcion}`}
        disabled={quitar.isPending}
        onClick={() => {
          if (window.confirm(confirmacionQuitarLinea(linea))) quitar.mutate()
        }}
      >
        Quitar
      </button>
      {quitar.error && (
        <span className="error" role="alert">
          {quitar.error.message}
        </span>
      )}
    </>
  )
}
