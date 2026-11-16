import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { CLAVE_CODIGOS_NO_ENCONTRADOS, listarCodigosNoEncontrados } from '../../api/productos'
import { formatoFecha } from './textos'

/**
 * HU-03.08 crit. 4: códigos que se buscaron y no existen, para que quien registra productos los dé de alta. Al
 * registrar el producto, su código sale de esta lista. No muestra nada si no hay pendientes.
 */
export function CodigosNoEncontrados() {
  const { data: codigos } = useQuery({ queryKey: CLAVE_CODIGOS_NO_ENCONTRADOS, queryFn: listarCodigosNoEncontrados })
  if (!codigos || codigos.length === 0) return null

  return (
    <section className="tarjeta" aria-label="Códigos no encontrados">
      <h2>Códigos no encontrados</h2>
      <p className="subtitulo">Se escanearon o escribieron, pero no existen en el catálogo.</p>
      <div className="tabla-contenedor">
        <table className="tabla">
          <thead>
            <tr>
              <th>Código</th>
              <th>Veces</th>
              <th>Última vez</th>
              <th>Caja</th>
              <th>
                <span className="solo-lector">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {codigos.map((fila) => (
              <tr key={fila.codigo}>
                <td>{fila.codigo}</td>
                <td>{fila.veces}</td>
                <td>{formatoFecha(fila.ultimaVez)}</td>
                <td>{fila.ultimaCaja ?? 'Sin caja'}</td>
                <td className="acciones">
                  <Link
                    to={`/catalogo/nuevo?${new URLSearchParams({ codigo: fila.codigo })}`}
                    aria-label={`Registrar el producto con código ${fila.codigo}`}
                  >
                    Registrar
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
