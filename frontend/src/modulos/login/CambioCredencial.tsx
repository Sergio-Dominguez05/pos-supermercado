import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { cambiarCredencial } from '../../api/autenticacion'
import { CLAVE_SESION, destinoInicial, useSesion } from './sesion'

/** HU-01.13: cada usuario cambia su contraseña, o su PIN si es Cajero. */
export function CambioCredencial() {
  const { data: usuario } = useSesion()
  if (!usuario) return null
  const esPin = usuario.rol === 'CAJERO'

  return (
    <main className="pagina">
      <h1>Cambiar {esPin ? 'PIN' : 'contraseña'}</h1>
      <p className="subtitulo">
        <Link to={destinoInicial(usuario.rol)}>Inicio</Link> · Mi {esPin ? 'PIN' : 'contraseña'}
      </p>
      <FormularioCredencial esPin={esPin} />
    </main>
  )
}

export function FormularioCredencial({ esPin }: { esPin: boolean }) {
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [hecho, setHecho] = useState(false)
  const clienteConsultas = useQueryClient()
  const nombre = esPin ? 'PIN' : 'contraseña'
  // Se avisa antes de enviar; el servidor también lo valida (crit. 3).
  const noCoincide = confirmacion !== '' && confirmacion !== nueva

  const cambio = useMutation({
    mutationFn: () => cambiarCredencial({ actual, nueva, confirmacion }),
    onMutate: () => setHecho(false),
    onSuccess: async () => {
      setActual('')
      setNueva('')
      setConfirmacion('')
      setHecho(true)
      // La credencial deja de ser temporal: se vuelve a leer la sesión.
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_SESION })
    },
  })

  const campo = (etiqueta: string, name: string, valor: string, cambiar: (v: string) => void, esNueva: boolean) => (
    <label>
      {etiqueta}
      <input
        type="password"
        name={name}
        required
        autoComplete={esNueva ? 'new-password' : 'current-password'}
        {...(esPin
          ? { inputMode: 'numeric', pattern: '\\d{4,6}', minLength: 4, maxLength: 6, title: 'De 4 a 6 dígitos' }
          : { minLength: esNueva ? 8 : undefined, maxLength: 128 })}
        value={valor}
        onChange={(evento) => cambiar(esPin ? evento.target.value.replace(/\D/g, '') : evento.target.value)}
      />
    </label>
  )

  return (
    <form
      className="tarjeta formulario"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (!noCoincide) cambio.mutate()
      }}
    >
      {campo(`${esPin ? 'PIN' : 'Contraseña'} actual`, 'actual', actual, setActual, false)}
      {campo(`${esPin ? 'Nuevo PIN' : 'Nueva contraseña'}`, 'nueva', nueva, setNueva, true)}
      <small>{esPin ? 'Numérico, de 4 a 6 dígitos.' : 'Al menos 8 caracteres, con letras y números.'} Distinto del actual.</small>
      {campo(`Confirmar ${esPin ? 'nuevo PIN' : 'nueva contraseña'}`, 'confirmacion', confirmacion, setConfirmacion, true)}

      {noCoincide && (
        <p className="error" role="alert">
          La confirmación no coincide con {esPin ? 'el nuevo PIN' : 'la nueva contraseña'}.
        </p>
      )}
      {cambio.error && (
        <p className="error" role="alert">
          {cambio.error.message}
        </p>
      )}
      {hecho && (
        <p className="exito" role="status">
          Se cambió su {nombre}.
        </p>
      )}
      <button type="submit" disabled={cambio.isPending || noCoincide}>
        {cambio.isPending ? 'Guardando…' : `Cambiar ${nombre}`}
      </button>
    </form>
  )
}
