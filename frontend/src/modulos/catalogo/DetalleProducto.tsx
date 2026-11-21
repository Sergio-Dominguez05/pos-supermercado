import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import {
  claveHistorialPrecios,
  claveProducto,
  obtenerHistorialPrecios,
  obtenerProducto,
  type CambioDePrecio,
  type Producto,
} from '../../api/productos'
import { useSesion } from '../login/sesion'
import { formatoFecha, formatoQuetzales } from './textos'

/**
 * El detalle de un producto, de solo lectura, para quien consulta el catálogo. HU-03.11 (decisión de Sergio): quien
 * consulta la bitácora (Supervisor y Administrador) ve además su historial de precios.
 */
export function DetalleProducto() {
  const { data: usuario } = useSesion()
  const id = Number(useParams().id)
  const puedeConsultar = Boolean(usuario?.permisos.includes('PRODUCTO_CONSULTAR'))
  const producto = useQuery({
    queryKey: claveProducto(id),
    queryFn: () => obtenerProducto(id),
    enabled: puedeConsultar && Number.isInteger(id),
  })
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Detalle del producto</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/catalogo">Catálogo</Link> · Detalle
      </p>
      {!puedeConsultar ? (
        <p className="tarjeta error">No tiene permiso para consultar el catálogo.</p>
      ) : producto.isPending ? (
        <p className="tarjeta">Cargando el producto…</p>
      ) : producto.error ? (
        <p className="tarjeta error">{producto.error.message}</p>
      ) : (
        <>
          <DatosDelProducto producto={producto.data} puedeEditar={usuario.permisos.includes('PRODUCTO_EDITAR')} />
          {usuario.permisos.includes('BITACORA_CONSULTAR') && <CargaHistorialPrecios id={id} />}
        </>
      )}
    </main>
  )
}

export function DatosDelProducto({ producto, puedeEditar }: { producto: Producto; puedeEditar: boolean }) {
  return (
    <section className="tarjeta" aria-label="Producto">
      <h2>{producto.descripcion}</h2>
      <dl className="resumen">
        <dt>Código</dt>
        <dd>{producto.codigoBarras}</dd>
        <dt>Precio</dt>
        <dd>
          <strong>{formatoQuetzales(producto.precio)}</strong>
        </dd>
        <dt>Estado</dt>
        <dd>{producto.activo ? 'Activo' : 'Inactivo'}</dd>
        <dt>Última modificación</dt>
        <dd>{formatoFecha(producto.modificadoEn)}</dd>
      </dl>
      {puedeEditar && <Link to={`/catalogo/${producto.id}/editar`}>Editar</Link>}
    </section>
  )
}

function CargaHistorialPrecios({ id }: { id: number }) {
  const historial = useQuery({ queryKey: claveHistorialPrecios(id), queryFn: () => obtenerHistorialPrecios(id) })
  if (historial.isPending) return <p className="tarjeta">Cargando el historial de precios…</p>
  if (historial.error) return <p className="tarjeta error">{historial.error.message}</p>
  return <HistorialPrecios cambios={historial.data} />
}

/**
 * HU-03.11 crit. 1 a 3: fecha y hora, precio anterior, precio nuevo y quién lo cambió, del más reciente al más
 * antiguo (así llegan del servidor). Sale de la bitácora, así que no se puede modificar: no hay acciones.
 */
export function HistorialPrecios({ cambios }: { cambios: CambioDePrecio[] }) {
  return (
    <section className="tarjeta" aria-label="Historial de precios">
      <h2>Historial de precios</h2>
      {cambios.length === 0 ? (
        <p>El precio no ha cambiado desde que se registró el producto.</p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha y hora</th>
                <th>Precio anterior</th>
                <th>Precio nuevo</th>
                <th>Cambiado por</th>
              </tr>
            </thead>
            <tbody>
              {cambios.map((cambio, i) => (
                <tr key={`${cambio.cambiadoEn}-${i}`}>
                  <td>{formatoFecha(cambio.cambiadoEn)}</td>
                  <td>{formatoQuetzales(cambio.anterior)}</td>
                  <td>{formatoQuetzales(cambio.nuevo)}</td>
                  <td>{cambio.usuario ?? 'Sistema'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="pendiente">Tomado de la bitácora: estos registros no se pueden modificar.</p>
    </section>
  )
}
