import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { cambiarCantidad, CLAVE_VENTA_EN_CURSO, type LineaVenta } from '../../api/ventas'
import { avisoSonoro } from '../catalogo/sonido'
import { CANTIDAD_NO_VALIDA, cantidadValida, conConfirmacion } from './cantidad'

/**
 * HU-06.04 crit. 2: la cantidad de una línea ya agregada se puede cambiar escribiendo la nueva y presionando Enter.
 * Escape deshace lo escrito. La línea y los totales se recalculan en el servidor y la pantalla los actualiza al
 * instante (HU-04.04 crit. 6). Se aplican las mismas reglas que al agregar: entero mayor que cero (crit. 3) y
 * confirmación por encima del límite (crit. 4). Al terminar, `onTerminado` devuelve el foco al campo del código.
 */
export function CantidadLinea({ linea, onTerminado }: { linea: LineaVenta; onTerminado?: () => void }) {
  const [texto, setTexto] = useState(String(linea.cantidad))
  const [aviso, setAviso] = useState<string>()
  const clienteConsultas = useQueryClient()
  const cambio = useMutation({
    mutationFn: (cantidad: number) => conConfirmacion((confirmada) => cambiarCantidad(linea.id, cantidad, confirmada)),
    onSuccess: (venta) => {
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
      onTerminado?.()
    },
    onError: (error) => {
      avisoSonoro()
      setAviso(error.message)
      setTexto(String(linea.cantidad))
    },
  })

  return (
    <form
      className="cantidad-linea"
      onSubmit={(evento) => {
        evento.preventDefault()
        const cantidad = cantidadValida(texto)
        if (cantidad === undefined) {
          avisoSonoro()
          setAviso(CANTIDAD_NO_VALIDA)
        } else if (cantidad !== linea.cantidad) {
          setAviso(undefined)
          cambio.mutate(cantidad)
        }
      }}
    >
      <input
        name="cantidad"
        inputMode="numeric"
        autoComplete="off"
        size={4}
        aria-label={`Cantidad de ${linea.descripcion}`}
        disabled={cambio.isPending}
        value={texto}
        onChange={(evento) => setTexto(evento.target.value)}
        onKeyDown={(evento) => {
          if (evento.key === 'Escape') {
            setTexto(String(linea.cantidad))
            setAviso(undefined)
          }
        }}
      />
      {aviso && (
        <span className="error" role="alert">
          {aviso}
        </span>
      )}
    </form>
  )
}
