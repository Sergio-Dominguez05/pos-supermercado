import { Link } from 'react-router'
import { useSesion } from '../login/sesion'
import { opcionesPara } from './opciones'

/** Pantalla principal tras iniciar sesión: muestra las opciones del rol (HU-01.01 crit. 1). */
export function Inicio() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>POS Supermercado</h1>
      <p className="subtitulo">
        {usuario.nombreCompleto} · {usuario.rolNombre}
      </p>

      <section className="tarjeta">
        <h2>Opciones de su rol</h2>
        <ul className="opciones">
          {opcionesPara(usuario.permisos).map((opcion) => (
            <li key={opcion.permiso}>
              {opcion.ruta ? (
                <Link to={opcion.ruta}>{opcion.etiqueta}</Link>
              ) : (
                <>
                  <span>{opcion.etiqueta}</span>
                  <span className="pendiente">Próximamente</span>
                </>
              )}
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
