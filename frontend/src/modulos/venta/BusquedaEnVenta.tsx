import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { agregarProducto, CLAVE_VENTA_EN_CURSO, type LineaVenta, type Venta } from '../../api/ventas'
import { BuscadorPorDescripcion } from '../catalogo/BuscadorPorDescripcion'
import { avisoSonoro, confirmacionSonora } from '../catalogo/sonido'
import { ConfirmacionAgregado } from './EscanerVenta'
import { lineaAgregada } from './lineas'

/**
 * HU-06.03 crit. 2: en la venta se abre la búsqueda por descripción (HU-03.07), se elige el producto y se agrega.
 * Si ya estaba en la venta, se suma a su línea (crit. 3). Todo con el teclado (crit. 4): flechas y Enter para elegir;
 * después de agregar, `onTerminado` devuelve el foco al campo del código.
 */
export function BusquedaEnVenta({ onTerminado }: { onTerminado?: () => void }) {
  const [agregada, setAgregada] = useState<LineaVenta>()
  const clienteConsultas = useQueryClient()
  const eleccion = useMutation({
    mutationFn: agregarProducto,
    onSuccess: (venta) => {
      setAgregada(lineaAgregada(clienteConsultas.getQueryData<Venta>(CLAVE_VENTA_EN_CURSO), venta))
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
      confirmacionSonora()
      onTerminado?.()
    },
    onError: () => avisoSonoro(),
  })

  return (
    <div>
      <BuscadorPorDescripcion onSeleccionado={(producto) => eleccion.mutate(producto.id)} />
      {eleccion.error && (
        <p className="aviso-alerta" role="alert">
          <strong>{eleccion.error.message}</strong>
        </p>
      )}
      {eleccion.isSuccess && <ConfirmacionAgregado linea={agregada} venta={eleccion.data} />}
    </div>
  )
}
