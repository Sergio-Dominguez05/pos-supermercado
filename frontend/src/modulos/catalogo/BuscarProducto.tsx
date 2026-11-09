import { Link } from 'react-router'
import { useSesion } from '../login/sesion'
import { BuscadorPorCodigo } from './BuscadorPorCodigo'

/**
 * Búsqueda rápida de productos para consultar el precio (EP-03). HU-03.06: por código. Los mismos buscadores se
 * usarán en la pantalla de venta (HU-06.01).
 */
export function BuscarProducto() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Buscar producto</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/catalogo">Catálogo</Link> · Buscar producto
      </p>
      {usuario.permisos.includes('PRODUCTO_CONSULTAR') ? (
        <section className="tarjeta">
          <h2>Por código</h2>
          <BuscadorPorCodigo />
        </section>
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar el catálogo.</p>
      )}
    </main>
  )
}
