import { useState } from 'react'
import { Link } from 'react-router'
import type { Producto } from '../../api/productos'
import { useSesion } from '../login/sesion'
import { BuscadorPorCodigo, ResultadoProducto } from './BuscadorPorCodigo'
import { BuscadorPorDescripcion } from './BuscadorPorDescripcion'

/**
 * Búsqueda rápida de productos para consultar el precio (EP-03): por código (HU-03.06) y por descripción
 * (HU-03.07). Los mismos buscadores se usarán en la pantalla de venta (HU-06.01).
 */
export function BuscarProducto() {
  const { data: usuario } = useSesion()
  const [elegido, setElegido] = useState<Producto>()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Buscar producto</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/catalogo">Catálogo</Link> · Buscar producto
      </p>
      {usuario.permisos.includes('PRODUCTO_CONSULTAR') ? (
        <>
          <section className="tarjeta">
            <h2>Por código</h2>
            <BuscadorPorCodigo />
          </section>
          <section className="tarjeta">
            <h2>Por descripción</h2>
            <BuscadorPorDescripcion onSeleccionado={setElegido} />
            {elegido && <ResultadoProducto producto={elegido} />}
          </section>
        </>
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar el catálogo.</p>
      )}
    </main>
  )
}
