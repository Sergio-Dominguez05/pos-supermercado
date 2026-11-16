import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { esCodigoNoEncontrado } from '../../api/productos'
import { agregarPorCodigo, CLAVE_VENTA_EN_CURSO, type LineaVenta, type Venta } from '../../api/ventas'
import { AvisoNoEncontrado } from '../catalogo/BuscadorPorCodigo'
import { avisoSonoro, confirmacionSonora } from '../catalogo/sonido'
import { formatoQuetzales } from '../catalogo/textos'
import { conConfirmacion, separarCantidad } from './cantidad'
import { lineaAgregada } from './lineas'

/** HU-06.04: el código sin el prefijo de cantidad, para el aviso de código no encontrado (HU-03.08). */
const codigoDe = (texto: string) => {
  const escaneo = separarCantidad(texto)
  return 'codigo' in escaneo ? escaneo.codigo : texto
}

/**
 * Envía lo escrito en el campo: con el prefijo "6*" agrega esa cantidad (HU-06.04 crit. 1), rechaza una cantidad que
 * no es un entero mayor que cero (crit. 3) y pide confirmar una cantidad mayor que el límite (crit. 4).
 */
function escanear(texto: string) {
  const escaneo = separarCantidad(texto)
  if ('error' in escaneo) return Promise.reject(new Error(escaneo.error))
  return conConfirmacion((confirmada) => agregarPorCodigo(escaneo.codigo, escaneo.cantidad, confirmada))
}

/**
 * HU-06.02: campo de la venta para el lector de código de barras, que escribe el código como un teclado y termina
 * con Enter. Cada código se envía al servidor, que agrega el producto (o suma 1 a su línea) y devuelve la venta con
 * los totales recalculados. Después de cada escaneo el texto queda seleccionado, así el siguiente lo reemplaza.
 *
 * - Crit. 3: confirmación visible ("Agregado: …") y sonora al agregar.
 * - Crit. 4: si el código no existe o el producto está inactivo, avisa como en EP-03 (HU-03.08 y HU-03.09) y no
 *   agrega nada.
 * - HU-06.04: "6*" antes del código agrega 6 unidades.
 */
export function EscanerVenta({ onBuscarPorDescripcion }: { onBuscarPorDescripcion?: () => void }) {
  const [codigo, setCodigo] = useState('')
  const [agregada, setAgregada] = useState<LineaVenta>()
  const campo = useRef<HTMLInputElement>(null)
  const clienteConsultas = useQueryClient()
  const escaneo = useMutation({
    mutationFn: escanear,
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
          placeholder="Escanee o escriba el código y presione Enter (6* antes para 6 unidades)"
          value={codigo}
          onChange={(evento) => setCodigo(evento.target.value)}
        />
      </label>
      {escaneo.error &&
        (esCodigoNoEncontrado(escaneo.error) ? (
          <AvisoNoEncontrado codigo={codigoDe(escaneo.variables ?? codigo)} onBuscarPorDescripcion={onBuscarPorDescripcion} />
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
