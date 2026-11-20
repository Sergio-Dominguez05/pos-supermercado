import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { CLAVE_FACTURAS_RECHAZADAS, listarFacturasRechazadas } from '../../api/facturas'
import { EstadoTurno } from '../caja/EstadoTurno'
import { useSesion } from '../login/sesion'
import { rotuloRechazadas } from '../facturas/textos'
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

      {/* HU-09.01 crit. 1: en una caja cerrada se pide abrir el turno. */}
      <EstadoTurno />

      {/* HU-08.06 crit. 3: se avisa al supervisor de las ventas pendientes de facturar. */}
      {usuario.permisos.includes('FACTURA_ANULAR') && <AvisoRechazadas />}

      <section className="tarjeta">
        <h2>Opciones de su rol</h2>
        <ul className="opciones">
          {opcionesPara(usuario.permisos).map((opcion) => (
            <li key={opcion.etiqueta}>
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

/** HU-08.06 crit. 3: el contador de ventas cuya factura rechazó el certificador; nada si no hay ninguna. */
export function AvisoRechazadas() {
  const { data: rechazadas = [] } = useQuery({
    queryKey: CLAVE_FACTURAS_RECHAZADAS,
    queryFn: listarFacturasRechazadas,
  })
  if (rechazadas.length === 0) return null
  return (
    <p className="tarjeta error" role="alert">
      {rechazadas.length === 1
        ? 'Hay una venta pendiente de facturar: el certificador rechazó su factura. '
        : `Hay ${rechazadas.length} ventas pendientes de facturar: el certificador rechazó sus facturas. `}
      <Link to="/facturas/rechazadas">{rotuloRechazadas(rechazadas.length)}</Link>
    </p>
  )
}
