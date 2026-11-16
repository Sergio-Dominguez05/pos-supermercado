import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useId, useState } from 'react'
import { buscarPorDescripcion, claveBusquedaDescripcion, MINIMO_BUSQUEDA, type Producto } from '../../api/productos'
import { formatoQuetzales } from './textos'

/** Espera tras la última tecla antes de buscar, para no consultar al servidor con cada letra. */
const ESPERA_MS = 250

/**
 * HU-03.07: busca productos activos por parte de su descripción. Desde 3 caracteres muestra los resultados con
 * código, descripción y precio (crit. 1 y 3); el servidor ignora mayúsculas y tildes (crit. 2). Con el teclado:
 * flechas para moverse, Enter para seleccionar y Escape para limpiar (crit. 4).
 *
 * La pantalla de venta (HU-06.01) lo reutiliza con `onSeleccionado` para agregar el producto.
 */
export function BuscadorPorDescripcion({ onSeleccionado }: { onSeleccionado: (producto: Producto) => void }) {
  const [texto, setTexto] = useState('')
  const [buscado, setBuscado] = useState('')
  const [marcado, setMarcado] = useState(0)
  const idLista = useId()

  useEffect(() => {
    const espera = setTimeout(() => setBuscado(texto.trim()), ESPERA_MS)
    return () => clearTimeout(espera)
  }, [texto])

  const suficiente = buscado.length >= MINIMO_BUSQUEDA
  const { data: resultados, error, isFetching } = useQuery({
    queryKey: claveBusquedaDescripcion(buscado),
    queryFn: () => buscarPorDescripcion(buscado),
    enabled: suficiente,
    placeholderData: keepPreviousData,
  })
  const visibles = suficiente ? (resultados ?? []) : []
  const actual = Math.min(marcado, Math.max(visibles.length - 1, 0))

  const seleccionar = (producto: Producto) => {
    onSeleccionado(producto)
    setTexto('')
    setBuscado('')
    setMarcado(0)
  }

  return (
    <div className="formulario" role="search" aria-label="Buscar por descripción">
      <label>
        Descripción
        <input
          name="descripcion"
          type="search"
          autoComplete="off"
          maxLength={100}
          placeholder={`Escriba al menos ${MINIMO_BUSQUEDA} letras`}
          role="combobox"
          aria-expanded={visibles.length > 0}
          aria-controls={idLista}
          aria-activedescendant={visibles.length > 0 ? `${idLista}-${actual}` : undefined}
          value={texto}
          onChange={(evento) => {
            setTexto(evento.target.value)
            setMarcado(0)
          }}
          onKeyDown={(evento) => {
            if (evento.key === 'ArrowDown') {
              evento.preventDefault()
              setMarcado(Math.min(actual + 1, visibles.length - 1))
            } else if (evento.key === 'ArrowUp') {
              evento.preventDefault()
              setMarcado(Math.max(actual - 1, 0))
            } else if (evento.key === 'Enter') {
              evento.preventDefault()
              if (visibles[actual]) seleccionar(visibles[actual])
            } else if (evento.key === 'Escape') {
              setTexto('')
              setBuscado('')
            }
          }}
        />
        <small>Flechas para moverse, Enter para elegir, Escape para limpiar.</small>
      </label>

      {error && (
        <p className="error" role="alert">
          {error.message}
        </p>
      )}
      {suficiente && !isFetching && resultados?.length === 0 && (
        <p className="pendiente">No hay productos activos con esa descripción.</p>
      )}
      {visibles.length > 0 && (
        <ResultadosDescripcion id={idLista} productos={visibles} marcado={actual} onElegir={seleccionar} />
      )}
    </div>
  )
}

export function ResultadosDescripcion({
  id,
  productos,
  marcado,
  onElegir,
}: {
  id: string
  productos: Producto[]
  marcado: number
  onElegir: (producto: Producto) => void
}) {
  return (
    <ul id={id} role="listbox" className="resultados" aria-label="Resultados">
      {productos.map((producto, indice) => (
        <li
          key={producto.id}
          id={`${id}-${indice}`}
          role="option"
          aria-selected={indice === marcado}
          className={indice === marcado ? 'resultado marcado' : 'resultado'}
          // onMouseDown, no onClick: así el campo no pierde el foco antes de elegir.
          onMouseDown={(evento) => {
            evento.preventDefault()
            onElegir(producto)
          }}
        >
          <span className="resultado-codigo">{producto.codigoBarras}</span>
          <span>{producto.descripcion}</span>
          <span className="resultado-precio">{formatoQuetzales(producto.precio)}</span>
        </li>
      ))}
    </ul>
  )
}
