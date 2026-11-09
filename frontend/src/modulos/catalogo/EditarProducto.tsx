import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  activarProducto,
  CLAVE_PRODUCTOS,
  claveProducto,
  desactivarProducto,
  editarProducto,
  obtenerProducto,
  type Producto,
} from '../../api/productos'
import { useSesion } from '../login/sesion'
import { avisoEstadoProducto, avisoProductoEditado, confirmacionDesactivarProducto } from './textos'

/** HU-03.02: el Administrador cambia la descripción y el precio de un producto. */
export function EditarProducto() {
  const { data: usuario } = useSesion()
  const id = Number(useParams().id)
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Editar producto</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Catálogo · Editar producto
      </p>
      {/* El servidor exige el permiso; esto solo evita mostrar un formulario que no se podrá usar. */}
      {usuario.permisos.includes('PRODUCTO_EDITAR') ? (
        <CargaProducto id={id} />
      ) : (
        <p className="tarjeta error">No tiene permiso para editar productos.</p>
      )}
    </main>
  )
}

function CargaProducto({ id }: { id: number }) {
  const { data: producto, error, isPending } = useQuery({ queryKey: claveProducto(id), queryFn: () => obtenerProducto(id) })

  if (isPending) return <p className="tarjeta">Cargando producto…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return (
    <>
      <FormularioEdicionProducto producto={producto} />
      <EstadoProducto producto={producto} />
    </>
  )
}

/** HU-03.03: activar o desactivar el producto. No hay opción de borrarlo. */
export function EstadoProducto({ producto }: { producto: Producto }) {
  const [aviso, setAviso] = useState<string>()
  const clienteConsultas = useQueryClient()
  const cambio = useMutation({
    mutationFn: () => (producto.activo ? desactivarProducto(producto.id) : activarProducto(producto.id)),
    onMutate: () => setAviso(undefined),
    onSuccess: async (cambiado) => {
      setAviso(avisoEstadoProducto(cambiado))
      clienteConsultas.setQueryData(claveProducto(cambiado.id), cambiado)
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_PRODUCTOS, exact: true })
    },
  })

  return (
    <section className="tarjeta formulario" aria-label="Estado del producto">
      <h2>Estado</h2>
      {aviso && (
        <p className="aviso" role="status">
          {aviso}
        </p>
      )}
      <p>
        <span className={`estado ${producto.activo ? 'estado-activo' : 'estado-inactivo'}`}>
          {producto.activo ? 'Activo' : 'Inactivo'}
        </span>{' '}
        {producto.activo ? 'Se puede agregar a las ventas.' : 'No se puede agregar a nuevas ventas.'}
      </p>
      {cambio.error && (
        <p className="error" role="alert">
          {cambio.error.message}
        </p>
      )}
      <button
        type="button"
        disabled={cambio.isPending}
        onClick={() => {
          if (producto.activo && !window.confirm(confirmacionDesactivarProducto(producto))) return
          cambio.mutate()
        }}
      >
        {producto.activo ? 'Desactivar producto' : 'Activar producto'}
      </button>
    </section>
  )
}

export function FormularioEdicionProducto({ producto }: { producto: Producto }) {
  const [descripcion, setDescripcion] = useState(producto.descripcion)
  const [precio, setPrecio] = useState(producto.precio.toFixed(2))
  const [aviso, setAviso] = useState<string>()
  const clienteConsultas = useQueryClient()

  const edicion = useMutation({
    mutationFn: () => editarProducto(producto.id, { descripcion, precio }),
    onMutate: () => setAviso(undefined),
    onSuccess: async (editado) => {
      setAviso(avisoProductoEditado(editado))
      setDescripcion(editado.descripcion)
      setPrecio(editado.precio.toFixed(2))
      clienteConsultas.setQueryData(claveProducto(editado.id), editado)
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_PRODUCTOS, exact: true })
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        edicion.mutate()
      }}
    >
      {aviso && (
        <p className="aviso" role="status">
          {aviso}
        </p>
      )}
      <label>
        Código de barras
        <input name="codigoBarras" value={producto.codigoBarras} disabled readOnly />
        <small>
          No se puede modificar. Si está mal, desactive el producto y registre uno nuevo con el código correcto.
        </small>
      </label>
      <label>
        Descripción
        <input
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
          autoComplete="off"
          value={precio}
          onChange={(evento) => setPrecio(evento.target.value)}
        />
        <small>Las líneas ya agregadas a una venta y las facturas emitidas conservan el precio anterior.</small>
      </label>

      {edicion.error && (
        <p className="error" role="alert">
          {edicion.error.message}
        </p>
      )}
      <button type="submit" disabled={edicion.isPending}>
        {edicion.isPending ? 'Guardando…' : 'Guardar cambios'}
      </button>
    </form>
  )
}
