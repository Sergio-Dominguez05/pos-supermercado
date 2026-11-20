import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CLAVE_PARAMETROS_OPERACION,
  guardarParametrosOperacion,
  obtenerParametrosOperacion,
  type ParametrosOperacion,
} from '../../api/parametrosOperacion'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'

/** HU-12.13: configuración administrativa de los parámetros de caja y venta. */
export function PaginaParametrosOperacion() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Parámetros de caja y venta</h1>
      <NavConfiguracion actual="Caja y venta" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaParametros />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaParametros() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_PARAMETROS_OPERACION,
    queryFn: obtenerParametrosOperacion,
  })

  if (isPending) return <p className="tarjeta">Cargando los parámetros…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioParametros guardado={data} />
}

export function FormularioParametros({ guardado }: { guardado: ParametrosOperacion }) {
  const clienteConsultas = useQueryClient()
  const [cantidadMaxima, setCantidadMaxima] = useState(String(guardado.cantidadMaximaPorLinea))
  const [cierreCiego, setCierreCiego] = useState(guardado.cierreCiego)
  const [aviso, setAviso] = useState<string>()

  const guardadoMut = useMutation({
    mutationFn: () => guardarParametrosOperacion({
      cantidadMaximaPorLinea: Number(cantidadMaxima),
      cierreCiego,
    }),
    onMutate: () => setAviso(undefined),
    onSuccess: (actualizado) => {
      clienteConsultas.setQueryData(CLAVE_PARAMETROS_OPERACION, actualizado)
      setCantidadMaxima(String(actualizado.cantidadMaximaPorLinea))
      setCierreCiego(actualizado.cierreCiego)
      setAviso('Los parámetros de caja y venta se guardaron correctamente.')
    },
  })

  return (
    <form
      className="tarjeta formulario"
      aria-label="Parámetros de caja y venta"
      onSubmit={(evento) => {
        evento.preventDefault()
        guardadoMut.mutate()
      }}
    >
      {aviso && <p className="exito" role="status">{aviso}</p>}
      <label>
        Cantidad máxima por línea
        <input
          name="cantidadMaximaPorLinea"
          type="number"
          inputMode="numeric"
          min={1}
          max={9999}
          step={1}
          required
          value={cantidadMaxima}
          onChange={(evento) => setCantidadMaxima(evento.target.value)}
        />
        <small>Al superar esta cantidad, el cajero debe confirmar antes de agregar el producto.</small>
      </label>
      <div>
        <label className="casilla">
          <input
            name="cierreCiego"
            type="checkbox"
            checked={cierreCiego}
            onChange={(evento) => setCierreCiego(evento.target.checked)}
          />
          Activar cierre ciego
        </label>
        <small>El cajero ingresará el cierre de lote sin ver primero el total registrado en el POS.</small>
      </div>
      {guardadoMut.error && <p className="error" role="alert">{guardadoMut.error.message}</p>}
      <button type="submit" disabled={guardadoMut.isPending}>
        {guardadoMut.isPending ? 'Guardando…' : 'Guardar'}
      </button>
    </form>
  )
}
