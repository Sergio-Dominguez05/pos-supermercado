import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CLAVE_ESTABLECIMIENTO,
  guardarEstablecimiento,
  obtenerEstablecimiento,
  type Establecimiento,
} from '../../api/establecimiento'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { AVISO_ESTABLECIMIENTO_GUARDADO } from './textos'

/** HU-12.02: el establecimiento registrado en la SAT. Solo para quien puede modificar la configuración. */
export function PaginaEstablecimiento() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Establecimiento SAT</h1>
      <NavConfiguracion actual="Establecimiento" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaEstablecimiento />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaEstablecimiento() {
  const { data, error, isPending } = useQuery({ queryKey: CLAVE_ESTABLECIMIENTO, queryFn: obtenerEstablecimiento })

  if (isPending) return <p className="tarjeta">Cargando el establecimiento…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioEstablecimiento guardado={data} />
}

/**
 * - Crit. 1: código de establecimiento (el número que asignó la SAT), nombre y dirección.
 * - Crit. 2: las facturas incluyen estos datos y guardan una copia al emitirse.
 * - Crit. 3: en V1 hay una sola sucursal; con EP-13 cada sucursal tendrá su propio establecimiento.
 */
export function FormularioEstablecimiento({ guardado }: { guardado: Establecimiento }) {
  const clienteConsultas = useQueryClient()
  const [codigoSat, setCodigoSat] = useState(guardado.codigoSat ?? '')
  const [nombre, setNombre] = useState(guardado.nombre ?? '')
  const [direccion, setDireccion] = useState(guardado.direccion ?? '')
  const [aviso, setAviso] = useState<string>()

  const guardadoMut = useMutation({
    mutationFn: () => guardarEstablecimiento({ codigoSat, nombre, direccion }),
    onMutate: () => setAviso(undefined),
    onSuccess: (actualizado) => {
      clienteConsultas.setQueryData(CLAVE_ESTABLECIMIENTO, actualizado)
      setAviso(AVISO_ESTABLECIMIENTO_GUARDADO)
    },
  })

  return (
    <form
      className="tarjeta formulario"
      aria-label="Establecimiento SAT"
      onSubmit={(evento) => {
        evento.preventDefault()
        guardadoMut.mutate()
      }}
    >
      {aviso && (
        <p className="exito" role="status">
          {aviso}
        </p>
      )}
      <label>
        Código de establecimiento
        <input
          name="codigoSat"
          required
          inputMode="numeric"
          maxLength={6}
          pattern="[1-9][0-9]{0,5}"
          title="El número que asignó la SAT: 1, 2, 3..."
          autoComplete="off"
          value={codigoSat}
          onChange={(evento) => setCodigoSat(evento.target.value.trim())}
        />
        <small>El número de establecimiento que aparece en su inscripción en la SAT (1, 2, 3...).</small>
      </label>
      <label>
        Nombre del establecimiento
        <input
          name="nombre"
          required
          maxLength={200}
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
        />
      </label>
      <label>
        Dirección
        <input
          name="direccion"
          required
          maxLength={300}
          value={direccion}
          onChange={(evento) => setDireccion(evento.target.value)}
        />
      </label>
      <p className="pendiente">
        Estos datos aparecen en cada factura, que guarda una copia al emitirse. Por ahora hay una sola sucursal; con
        varias sucursales, cada una tendrá su propio establecimiento.
      </p>

      {guardadoMut.error && (
        <p className="error" role="alert">
          {guardadoMut.error.message}
        </p>
      )}
      <button type="submit" disabled={guardadoMut.isPending}>
        {guardadoMut.isPending ? 'Guardando…' : 'Guardar'}
      </button>
      {guardado.modificadoEn && (
        <p className="pendiente">Última modificación: {new Date(guardado.modificadoEn).toLocaleString('es-GT')}.</p>
      )}
    </form>
  )
}
