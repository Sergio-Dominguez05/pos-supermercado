import { useSesion } from '../login/sesion'

/** Destino del Cajero al iniciar sesión (HU-01.02). La pantalla de venta llega con HU-06.01. */
export function Venta() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Venta</h1>
      <p className="subtitulo">
        {usuario.nombreCompleto} · {usuario.rolNombre}
      </p>

      <section className="tarjeta">
        <p>La pantalla de venta todavía no está disponible.</p>
      </section>
    </main>
  )
}
