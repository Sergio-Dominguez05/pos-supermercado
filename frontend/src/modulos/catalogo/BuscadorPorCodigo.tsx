import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { buscarPorCodigo, esCodigoNoEncontrado, type Producto } from '../../api/productos'
import { avisoSonoro } from './sonido'
import { avisoCodigoNoEncontrado, avisoProductoInactivo, formatoQuetzales } from './textos'

/**
 * HU-03.06: busca un producto por su código exacto. Pensado para el lector de código de barras, que escribe el
 * código como un teclado y termina con Enter (crit. 2): el Enter dispara la búsqueda. Si el código está dañado, el
 * cajero lo escribe a mano y presiona Enter (crit. 3). Después de cada búsqueda el texto queda seleccionado, así el
 * siguiente escaneo lo reemplaza sin borrar nada.
 *
 * HU-03.08: si el código no existe, avisa de forma visible y sonora (crit. 1) y ofrece buscar por descripción
 * (crit. 2). El servidor deja registrado el código para el administrador (crit. 4).
 *
 * HU-03.09 crit. 2: si el producto existe pero está inactivo, avisa que no está disponible para la venta y no se lo
 * entrega a `onEncontrado`. El servidor también lo rechaza al vender (`GestionProductos.paraVender`).
 *
 * La pantalla de venta (HU-06.01) lo reutiliza con `onEncontrado` para agregar el producto.
 */
export function BuscadorPorCodigo({
  onEncontrado,
  onBuscarPorDescripcion,
}: {
  onEncontrado?: (producto: Producto) => void
  onBuscarPorDescripcion?: () => void
}) {
  const [codigo, setCodigo] = useState('')
  const campo = useRef<HTMLInputElement>(null)
  const busqueda = useMutation({
    mutationFn: buscarPorCodigo,
    onSuccess: (producto) => {
      if (producto.activo) onEncontrado?.(producto)
      else avisoSonoro()
    },
    onError: (error) => {
      if (esCodigoNoEncontrado(error)) avisoSonoro()
    },
    onSettled: () => campo.current?.select(),
  })

  return (
    <form
      className="formulario"
      role="search"
      aria-label="Buscar por código"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (codigo.trim()) busqueda.mutate(codigo)
      }}
    >
      <label>
        Código de barras
        <input
          ref={campo}
          name="codigo"
          autoFocus
          autoComplete="off"
          inputMode="text"
          maxLength={50}
          placeholder="Escanee o escriba el código y presione Enter"
          value={codigo}
          onChange={(evento) => setCodigo(evento.target.value)}
        />
      </label>
      {busqueda.error &&
        (esCodigoNoEncontrado(busqueda.error) ? (
          <AvisoNoEncontrado codigo={busqueda.variables ?? codigo} onBuscarPorDescripcion={onBuscarPorDescripcion} />
        ) : (
          <p className="error" role="alert">
            {busqueda.error.message}
          </p>
        ))}
      {busqueda.data && !busqueda.error && <ResultadoEscaneo producto={busqueda.data} />}
    </form>
  )
}

/** HU-03.08 crit. 1 y 2: aviso visible de que el código no existe, con la opción de buscar por descripción. */
export function AvisoNoEncontrado({
  codigo,
  onBuscarPorDescripcion,
}: {
  codigo: string
  onBuscarPorDescripcion?: () => void
}) {
  return (
    <div className="aviso-alerta" role="alert">
      <p>
        <strong>{avisoCodigoNoEncontrado(codigo)}</strong>
      </p>
      <p>No se agregó ningún producto. El código quedó anotado para que un administrador lo registre.</p>
      {onBuscarPorDescripcion && (
        <button type="button" onClick={onBuscarPorDescripcion}>
          Buscar por descripción
        </button>
      )}
    </div>
  )
}

export function ResultadoProducto({ producto }: { producto: Producto }) {
  return (
    <dl className="aviso" aria-live="polite">
      <dt>Código</dt>
      <dd>{producto.codigoBarras}</dd>
      <dt>Descripción</dt>
      <dd>{producto.descripcion}</dd>
      <dt>Precio</dt>
      <dd>{formatoQuetzales(producto.precio)}</dd>
      {!producto.activo && (
        <>
          <dt>Estado</dt>
          <dd>Inactivo</dd>
        </>
      )}
    </dl>
  )
}

/** HU-03.09: el resultado de un escaneo; si el producto está inactivo, lo marca como no disponible para la venta. */
export function ResultadoEscaneo({ producto }: { producto: Producto }) {
  return (
    <>
      {!producto.activo && (
        <p className="aviso-alerta" role="alert">
          <strong>{avisoProductoInactivo(producto)}</strong>
        </p>
      )}
      <ResultadoProducto producto={producto} />
    </>
  )
}
