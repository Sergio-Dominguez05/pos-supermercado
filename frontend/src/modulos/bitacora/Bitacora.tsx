import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { verificarBitacora, type VerificacionBitacora } from '../../api/bitacora'
import { useSesion } from '../login/sesion'

/**
 * Bitácora. Por ahora ofrece verificar su integridad (HU-11.05 crit. 3); la consulta de registros llega con
 * HU-11.06. No existe ninguna opción para editar ni borrar registros (crit. 1).
 */
export function Bitacora() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Bitácora</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Bitácora
      </p>
      {usuario.permisos.includes('BITACORA_CONSULTAR') ? (
        <VerificarIntegridad />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar la bitácora.</p>
      )}
    </main>
  )
}

function VerificarIntegridad() {
  const verificacion = useMutation({ mutationFn: verificarBitacora })

  return (
    <section className="tarjeta formulario">
      <h2>Integridad</h2>
      <p>
        Cada registro está encadenado con el anterior. Verificar recalcula toda la cadena y avisa si falta algún
        registro o si alguno fue alterado.
      </p>
      {verificacion.error && (
        <p className="error" role="alert">
          {verificacion.error.message}
        </p>
      )}
      {verificacion.data && <ResultadoVerificacion resultado={verificacion.data} />}
      <button type="button" disabled={verificacion.isPending} onClick={() => verificacion.mutate()}>
        {verificacion.isPending ? 'Verificando…' : 'Verificar integridad'}
      </button>
    </section>
  )
}

export function ResultadoVerificacion({ resultado }: { resultado: VerificacionBitacora }) {
  if (resultado.integra) {
    return (
      <p className="exito" role="status">
        La bitácora está íntegra: {resultado.registros} registros encadenados, sin faltantes ni alteraciones.
      </p>
    )
  }
  return (
    <div className="error" role="alert">
      <p>La bitácora no está íntegra. Problemas encontrados:</p>
      <ul>
        {resultado.problemas.map((problema) => (
          <li key={`${problema.secuencia}-${problema.descripcion}`}>
            Registro {problema.secuencia}: {problema.descripcion}
          </li>
        ))}
      </ul>
    </div>
  )
}
