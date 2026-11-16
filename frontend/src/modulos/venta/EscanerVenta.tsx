import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { esCodigoNoEncontrado } from '../../api/productos'
import { agregarPorCodigo, CLAVE_VENTA_EN_CURSO, type LineaVenta, type Venta } from '../../api/ventas'
import { AvisoNoEncontrado } from '../catalogo/BuscadorPorCodigo'
import { avisoSonoro, confirmacionSonora } from '../catalogo/sonido'
import { formatoQuetzales } from '../catalogo/textos'
import { lineaAgregada } from './lineas'

/**
 * HU-06.02: campo de la venta para el lector de código de barras, que escribe el código como un teclado y termina
 * con Enter. Cada código se envía al servidor, que agrega el producto (o suma 1 a su línea) y devuelve la venta con
 * los totales recalculados. Después de cada escaneo el texto queda seleccionado, así el siguiente lo reemplaza.
 *
 * - Crit. 3: confirmación visible ("Agregado: …") y sonora al agregar.
 * - Crit. 4: si el código no existe o el producto está inactivo, avisa como en EP-03 (HU-03.08 y HU-03.09) y no
 *   agrega nada.
 */
export function EscanerVenta({ onBuscarPorDescripcion }: { onBuscarPorDescripcion?: () => void }) {
  const [codigo, setCodigo] = useState('')
  const [agregada, setAgregada] = useState<LineaVenta>()
  const campo = useRef<HTMLInputElement>(null)
  const clienteConsultas = useQueryClient()
  const escaneo = useMutation({
    mutationFn: agregarPorCodigo,
    onSuccess: (venta) => {
      setAgregada(lineaAgregada(clienteConsultas.getQueryData<Venta>(CLAVE_VENTA_EN_CURSO), venta))
      clienteConsultas.setQueryData(CLAVE_VENTA_EN_CURSO, venta)
      confirmacionSonora()
    },
    onError: () => avisoSonoro(),
    onSettled: () => campo.current?.select(),
  })

  return (
    <form
      className="formulario"
      aria-label="Agregar producto"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (codigo.trim()) escaneo.mutate(codigo)
      }}
    >
      <label>
        Código de producto
        <input
          ref={campo}
          name="codigo"
          autoFocus
          autoComplete="off"
          maxLength={100}
          placeholder="Escanee o escriba el código y presione Enter"
          value={codigo}
          onChange={(evento) => setCodigo(evento.target.value)}
        />
      </label>
      {escaneo.error &&
        (esCodigoNoEncontrado(escaneo.error) ? (
          <AvisoNoEncontrado codigo={escaneo.variables ?? codigo} onBuscarPorDescripcion={onBuscarPorDescripcion} />
        ) : (
          <p className="aviso-alerta" role="alert">
            <strong>{escaneo.error.message}</strong>
          </p>
        ))}
      {escaneo.isSuccess && <ConfirmacionAgregado linea={agregada} venta={escaneo.data} />}
    </form>
  )
}

/** HU-06.02 crit. 3: confirmación visible de lo que se agregó. */
export function ConfirmacionAgregado({ linea, venta }: { linea: LineaVenta | undefined; venta: Venta }) {
  if (!linea) return null
  return (
    <p className="aviso" role="status">
      Agregado: {linea.descripcion} · {formatoQuetzales(linea.precioUnitario)}
      {linea.cantidad > 1 && ` (${linea.cantidad} en la venta)`} · Total {formatoQuetzales(venta.total)}
    </p>
  )
}
