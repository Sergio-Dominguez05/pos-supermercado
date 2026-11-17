import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { CLAVE_CAJAS, claveCaja, crearCaja, editarCaja, obtenerCaja, type Caja } from '../../api/cajas'
import { CLAVE_IMPRESORAS, listarImpresoras } from '../../api/impresoras'
import { useSesion } from '../login/sesion'
import { avisoCajaGuardada } from './textos'

/** HU-12.05: alta de una caja (sin `id` en la ruta) o edición de una existente. */
export function PaginaCaja() {
  const { data: usuario } = useSesion()
  const parametro = useParams().id
  if (!usuario) return null
  const id = parametro ? Number(parametro) : undefined

  return (
    <main className="pagina">
      <h1>{id ? 'Editar caja' : 'Nueva caja'}</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/configuracion/cajas">Cajas</Link> · {id ? 'Editar' : 'Nueva'}
      </p>
      {!usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      ) : id ? (
        <CargaCaja id={id} />
      ) : (
        <FormularioCaja />
      )}
    </main>
  )
}

function CargaCaja({ id }: { id: number }) {
  const { data: caja, error, isPending } = useQuery({ queryKey: claveCaja(id), queryFn: () => obtenerCaja(id) })

  if (isPending) return <p className="tarjeta">Cargando caja…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioCaja caja={caja} />
}

export function FormularioCaja({ caja }: { caja?: Caja }) {
  const [nombre, setNombre] = useState(caja?.nombre ?? '')
  const [serieFel, setSerieFel] = useState(caja?.serieFel ?? '')
  const [impresora, setImpresora] = useState(caja?.impresoraId ? String(caja.impresoraId) : '')
  const impresoras = useQuery({ queryKey: CLAVE_IMPRESORAS, queryFn: listarImpresoras })
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()

  const guardado = useMutation({
    mutationFn: () => {
      const datos = { nombre, serieFel, impresoraId: impresora ? Number(impresora) : null }
      return caja ? editarCaja(caja.id, datos) : crearCaja(datos)
    },
    // Vuelve a la lista ya actualizada con el aviso. Si falla (p. ej. nombre repetido), se queda aquí con el error.
    onSuccess: async (guardada) => {
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_CAJAS })
      navegar('/configuracion/cajas', { state: { aviso: avisoCajaGuardada(guardada, !caja) } })
    },
  })

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        guardado.mutate()
      }}
    >
      <label>
        Número o nombre
        <input
          name="nombre"
          required
          maxLength={40}
          placeholder="Caja 1"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
        />
        <small>No se puede repetir, sin importar mayúsculas.</small>
      </label>
      <label>
        Serie de facturación (opcional)
        <input
          name="serieFel"
          maxLength={20}
          pattern="[A-Za-z0-9]*"
          title="Solo letras y números, sin espacios"
          autoComplete="off"
          value={serieFel}
          onChange={(evento) => setSerieFel(evento.target.value.toUpperCase())}
        />
        <small>Déjela vacía si la caja no tiene una serie propia.</small>
      </label>
      <label>
        Impresora
        <select name="impresoraId" value={impresora} onChange={(evento) => setImpresora(evento.target.value)}>
          <option value="">Sin impresora</option>
          {impresoras.data?.map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre} ({opcion.anchoMm} mm)
            </option>
          ))}
        </select>
        <small>
          Donde salen las facturas de esta caja (HU-12.08). Las impresoras se agregan en{' '}
          <Link to="/configuracion/impresoras">Impresoras</Link>.
        </small>
      </label>

      {guardado.error && (
        <p className="error" role="alert">
          {guardado.error.message}
        </p>
      )}
      <button type="submit" disabled={guardado.isPending}>
        {guardado.isPending ? 'Guardando…' : caja ? 'Guardar cambios' : 'Crear caja'}
      </button>
    </form>
  )
}
