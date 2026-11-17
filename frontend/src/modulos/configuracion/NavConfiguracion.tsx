import { Link } from 'react-router'

const SECCIONES = [
  { ruta: '/configuracion/cajas', etiqueta: 'Cajas' },
  { ruta: '/configuracion/emisor', etiqueta: 'Emisor' },
  { ruta: '/configuracion/establecimiento', etiqueta: 'Establecimiento' },
  { ruta: '/configuracion/fel', etiqueta: 'Conexión FEL' },
] as const

/** Ruta de navegación de la configuración (EP-12): la sección actual sin enlace y las demás con él. */
export function NavConfiguracion({ actual }: { actual: (typeof SECCIONES)[number]['etiqueta'] }) {
  return (
    <p className="subtitulo">
      <Link to="/inicio">Inicio</Link> · Configuración
      {SECCIONES.map((seccion) => (
        <span key={seccion.ruta}>
          {' · '}
          {seccion.etiqueta === actual ? (
            <strong>{seccion.etiqueta}</strong>
          ) : (
            <Link to={seccion.ruta}>{seccion.etiqueta}</Link>
          )}
        </span>
      ))}
    </p>
  )
}
