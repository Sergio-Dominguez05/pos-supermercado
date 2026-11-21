import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { editarCliente, type Cliente } from '../../api/clientes'
import { CORREO_INVALIDO, correoValido, IDENTIFICACION_NO_EDITABLE, identificacionDe } from './textos'

/** Los datos del cliente que se muestran y se corrigen. */
export type DatosEditables = Pick<Cliente, 'id' | 'nit' | 'nombre' | 'direccion' | 'correo' | 'tipoIdentificacion'>

/**
 * HU-05.09 Editar los datos de un cliente.
 * - Crit. 1: nombre, dirección y correo, también durante la venta.
 * - Crit. 2: el NIT o CUI se muestra pero no se edita; si está mal, se registra un cliente nuevo.
 * - Crit. 3 y 4 los cumple el servidor: las facturas emitidas no cambian y la edición queda en la bitácora.
 */
export function EdicionCliente({
  cliente,
  onGuardado,
  onCancelar,
}: {
  cliente: DatosEditables
  onGuardado: (cliente: Cliente) => void
  onCancelar: () => void
}) {
  const [nombre, setNombre] = useState(cliente.nombre)
  const [direccion, setDireccion] = useState(cliente.direccion)
  const [correo, setCorreo] = useState(cliente.correo ?? '')
  const [problema, setProblema] = useState<string>()
  const edicion = useMutation({
    mutationFn: () => editarCliente(cliente.id, { nombre, direccion, correo }),
    onSuccess: onGuardado,
  })

  return (
    <form
      className="formulario"
      aria-label="Editar cliente"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (correo.trim() !== '' && !correoValido(correo)) {
          setProblema(CORREO_INVALIDO)
          return
        }
        setProblema(undefined)
        edicion.mutate()
      }}
    >
      <p>
        <strong>{identificacionDe(cliente)}</strong>
        <br />
        <small>{IDENTIFICACION_NO_EDITABLE}</small>
      </p>
      <label>
        Nombre del cliente
        <input name="nombreCliente" required maxLength={200} value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </label>
      <label>
        Dirección
        <input
          name="direccionCliente"
          maxLength={300}
          placeholder="Ciudad"
          value={direccion}
          onChange={(e) => setDireccion(e.target.value)}
        />
      </label>
      <label>
        Correo (opcional)
        <input name="correoCliente" type="email" maxLength={254} value={correo} onChange={(e) => setCorreo(e.target.value)} />
      </label>
      {(problema ?? edicion.error) && (
        <p className="error" role="alert">
          {problema ?? edicion.error?.message}
        </p>
      )}
      <div className="acciones">
        <button type="submit" disabled={edicion.isPending}>
          {edicion.isPending ? 'Guardando…' : 'Guardar cambios'}
        </button>
        <button type="button" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
