import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { CLAVE_PRODUCTOS, crearProducto } from '../../api/productos'
import { useSesion } from '../login/sesion'
import { avisoProductoCreado } from './textos'

/** HU-03.01: el Administrador registra los productos que vende el supermercado. */
export function NuevoProducto() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Nuevo producto</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Catálogo · Nuevo producto
      </p>
      {/* El servidor exige el permiso; esto solo evita mostrar un formulario que no se podrá usar. */}
      {usuario.permisos.includes('PRODUCTO_EDITAR') ? (
        <FormularioProducto />
      ) : (
        <p className="tarjeta error">No tiene permiso para crear productos.</p>
      )}
    </main>
  )
}

export function FormularioProducto() {
  const [codigoBarras, setCodigoBarras] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [precio, setPrecio] = useState('')
  const [aviso, setAviso] = useState<string>()
  const campoCodigo = useRef<HTMLInputElement>(null)
  const campoDescripcion = useRef<HTMLInputElement>(null)
  const clienteConsultas = useQueryClient()

  const creacion = useMutation({
    mutationFn: () => crearProducto({ codigoBarras, descripcion, precio }),
    // Se queda en el formulario, vacío y listo para escanear el siguiente producto.
    onSuccess: async (nuevo) => {
      setAviso(avisoProductoCreado(nuevo))
      setCodigoBarras('')
      setDescripcion('')
      setPrecio('')
      campoCodigo.current?.focus()
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_PRODUCTOS })
    },
    onMutate: () => setAviso(undefined),
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        creacion.mutate()
      }}
    >
      {aviso && (
        <p className="aviso" role="status">
          {aviso}
        </p>
      )}
      <label>
        Código de barras
        <input
          ref={campoCodigo}
          name="codigoBarras"
          required
          autoFocus
          maxLength={50}
          autoComplete="off"
          value={codigoBarras}
          onChange={(evento) => setCodigoBarras(evento.target.value)}
          onKeyDown={(evento) => {
            // El lector escribe el código y envía Enter (crit. 3): se pasa a la descripción en vez de guardar.
            if (evento.key === 'Enter') {
              evento.preventDefault()
              campoDescripcion.current?.focus()
            }
          }}
        />
        <small>Escanéelo con el lector o escríbalo. No se puede repetir.</small>
      </label>
      <label>
        Descripción
        <input
          ref={campoDescripcion}
          name="descripcion"
          required
          maxLength={100}
          autoComplete="off"
          value={descripcion}
          onChange={(evento) => setDescripcion(evento.target.value)}
        />
        <small>Máximo 100 caracteres.</small>
      </label>
      <label>
        Precio con IVA (Q)
        <input
          name="precio"
          required
          inputMode="decimal"
          pattern="\d+([.,]\d{1,2})?"
          title="Mayor que cero, con 2 decimales como máximo"
          placeholder="0.00"
          autoComplete="off"
          value={precio}
          onChange={(evento) => setPrecio(evento.target.value)}
        />
        <small>Mayor que cero, con 2 decimales como máximo.</small>
      </label>

      {creacion.error && (
        <p className="error" role="alert">
          {creacion.error.message}
        </p>
      )}
      <button type="submit" disabled={creacion.isPending}>
        {creacion.isPending ? 'Guardando…' : 'Registrar producto'}
      </button>
    </form>
  )
}
