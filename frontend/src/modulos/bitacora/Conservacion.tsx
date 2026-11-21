import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  archivarBitacora,
  cambiarPlazoConservacion,
  CLAVE_CONSERVACION_BITACORA,
  obtenerConservacion,
  type ConservacionBitacora,
} from '../../api/bitacora'
import { fechaYHoraConSegundos } from './consulta'

/**
 * HU-11.10 Conservar la bitácora: el plazo, cuántos registros hay y, para el Administrador, cambiar el plazo y
 * archivar lo antiguo. Nada se elimina nunca (crit. 3).
 */
export function Conservacion({ esAdministrador }: { esAdministrador: boolean }) {
  const { data, error } = useQuery({ queryKey: CLAVE_CONSERVACION_BITACORA, queryFn: obtenerConservacion })
  if (error) return <p className="tarjeta error">{error.message}</p>
  if (!data) return null
  return <EstadoConservacion estado={data} esAdministrador={esAdministrador} />
}

export function EstadoConservacion({
  estado,
  esAdministrador,
}: {
  estado: ConservacionBitacora
  esAdministrador: boolean
}) {
  return (
    <section className="tarjeta formulario" aria-label="Conservación de la bitácora">
      <h2>Conservación</h2>
      <p>
        Los registros se conservan por lo menos <strong>{estado.plazoAnios} años</strong> y el sistema nunca los elimina,
        ni automáticamente ni a pedido. Los antiguos se pueden archivar: se siguen consultando, exportando y verificando
        igual que los demás.
      </p>
      <dl className="resumen">
        <dt>Registros activos</dt>
        <dd>{estado.registrosActivos}</dd>
        <dt>Registros archivados</dt>
        <dd>{estado.registrosArchivados}</dd>
        <dt>Registro más antiguo</dt>
        <dd>{estado.registroMasAntiguo ? fechaYHoraConSegundos(estado.registroMasAntiguo) : '—'}</dd>
        <dt>Archivado hasta</dt>
        <dd>{estado.archivadoHasta ? fechaYHoraConSegundos(estado.archivadoHasta) : 'Nada archivado'}</dd>
      </dl>
      {esAdministrador && (
        <>
          <PlazoConservacion estado={estado} />
          <ArchivarRegistros estado={estado} />
        </>
      )}
    </section>
  )
}

/** Crit. 1: el plazo en años, nunca menos del mínimo. */
function PlazoConservacion({ estado }: { estado: ConservacionBitacora }) {
  const consultas = useQueryClient()
  const [anios, setAnios] = useState(String(estado.plazoAnios))
  const cambio = useMutation({
    mutationFn: () => cambiarPlazoConservacion(Number(anios)),
    onSuccess: (actual) => consultas.setQueryData(CLAVE_CONSERVACION_BITACORA, actual),
  })
  return (
    <form
      className="en-linea"
      aria-label="Plazo de conservación"
      onSubmit={(evento) => {
        evento.preventDefault()
        cambio.mutate()
      }}
    >
      <label>
        Plazo de conservación (años)
        <input
          name="plazoAnios"
          type="number"
          min={estado.plazoMinimoAnios}
          max={50}
          required
          value={anios}
          onChange={(evento) => setAnios(evento.target.value)}
        />
      </label>
      <button type="submit" disabled={cambio.isPending || Number(anios) === estado.plazoAnios}>
        {cambio.isPending ? 'Guardando…' : 'Guardar plazo'}
      </button>
      <small>Mínimo {estado.plazoMinimoAnios} años.</small>
      {cambio.error && (
        <p className="error" role="alert">
          {cambio.error.message}
        </p>
      )}
    </form>
  )
}

/** Crit. 2: archiva lo anterior a una fecha de hace más de 90 días, con confirmación. */
function ArchivarRegistros({ estado }: { estado: ConservacionBitacora }) {
  const consultas = useQueryClient()
  const [antesDe, setAntesDe] = useState(estado.archivableAntesDe)
  const archivo = useMutation({
    mutationFn: () => archivarBitacora(antesDe),
    onSuccess: (resultado) => consultas.setQueryData(CLAVE_CONSERVACION_BITACORA, resultado.estado),
  })
  return (
    <form
      className="en-linea"
      aria-label="Archivar registros antiguos"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (window.confirm(`¿Archivar los registros anteriores al ${antesDe}? Se seguirán consultando; no se borra nada.`))
          archivo.mutate()
      }}
    >
      <label>
        Archivar lo anterior al
        <input
          name="antesDe"
          type="date"
          required
          max={estado.archivableAntesDe}
          value={antesDe}
          onChange={(evento) => setAntesDe(evento.target.value)}
        />
      </label>
      <button type="submit" disabled={archivo.isPending}>
        {archivo.isPending ? 'Archivando…' : 'Archivar'}
      </button>
      <small>Solo registros de hace más de 90 días.</small>
      {archivo.error && (
        <p className="error" role="alert">
          {archivo.error.message}
        </p>
      )}
      {archivo.data && (
        <p className="exito" role="status">
          {archivo.data.archivados === 1 ? 'Se archivó 1 registro.' : `Se archivaron ${archivo.data.archivados} registros.`}
        </p>
      )}
    </form>
  )
}
