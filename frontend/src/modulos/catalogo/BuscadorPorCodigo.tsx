import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { buscarPorCodigo, type Producto } from '../../api/productos'
import { formatoQuetzales } from './textos'

/**
 * HU-03.06: busca un producto por su código exacto. Pensado para el lector de código de barras, que escribe el
 * código como un teclado y termina con Enter (crit. 2): el Enter dispara la búsqueda. Si el código está dañado, el
 * cajero lo escribe a mano y presiona Enter (crit. 3). Después de cada búsqueda el texto queda seleccionado, así el
 * siguiente escaneo lo reemplaza sin borrar nada.
 *
 * La pantalla de venta (HU-06.01) lo reutiliza con `onEncontrado` para agregar el producto.
 */
export function BuscadorPorCodigo({ onEncontrado }: { onEncontrado?: (producto: Producto) => void }) {
  const [codigo, setCodigo] = useState('')
  const campo = useRef<HTMLInputElement>(null)
  const busqueda = useMutation({
    mutationFn: buscarPorCodigo,
    onSuccess: (producto) => onEncontrado?.(producto),
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
      {busqueda.error && (
        <p className="error" role="alert">
          {busqueda.error.message}
        </p>
      )}
      {busqueda.data && !busqueda.error && <ResultadoProducto producto={busqueda.data} />}
    </form>
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
