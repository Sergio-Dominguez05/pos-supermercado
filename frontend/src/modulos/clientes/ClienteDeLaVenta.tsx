import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  asignarClienteAVenta,
  buscarClientePorNit,
  claveClienteDeVenta,
  obtenerClienteDeVenta,
  registrarCliente,
  type BusquedaNit,
  type Cliente,
} from '../../api/clientes'
import { nitValido, normalizarNit } from './nit'
import { avisoBusqueda, describirCliente, NIT_CLIENTE_INVALIDO } from './textos'

/**
 * EP-05: el cliente de la venta en curso.
 * - HU-05.01: cada venta empieza a Consumidor Final; volver a CF es un solo clic.
 * - HU-05.02: con el NIT (con o sin guion) se cargan el nombre y la dirección del cliente registrado.
 * - HU-05.03: un NIT mal escrito se avisa y no se usa.
 * - HU-05.04: un NIT nuevo se consulta al certificador; si no aparece o no responde, se escribe el nombre o se usa CF.
 * - HU-05.05: el cliente nuevo se registra sin salir de la venta (dirección "Ciudad" si se deja vacía).
 * F6 lleva al campo del NIT (HU-06.17).
 */
export function ClienteDeLaVenta({ ventaId }: { ventaId: number }) {
  const clave = claveClienteDeVenta(ventaId)
  const clienteConsultas = useQueryClient()
  const { data } = useQuery({ queryKey: clave, queryFn: obtenerClienteDeVenta })
  const [nit, setNit] = useState('')
  const [problema, setProblema] = useState<string>()
  const [busqueda, setBusqueda] = useState<BusquedaNit>()

  const asignacion = useMutation({
    mutationFn: (cliente: Cliente | null) => asignarClienteAVenta(cliente?.id ?? null),
    onSuccess: (actual) => clienteConsultas.setQueryData(clave, actual),
  })
  const consulta = useMutation({
    mutationFn: () => buscarClientePorNit(nit),
    onMutate: () => setBusqueda(undefined),
    onSuccess: (resultado) => {
      setBusqueda(resultado)
      if (resultado.cliente) asignacion.mutate(resultado.cliente)
    },
  })

  const buscar = () => {
    asignacion.reset()
    if (!nitValido(nit)) {
      setProblema(NIT_CLIENTE_INVALIDO)
      return
    }
    setProblema(undefined)
    consulta.mutate()
  }
  const aConsumidorFinal = () => {
    setNit('')
    setProblema(undefined)
    setBusqueda(undefined)
    consulta.reset()
    asignacion.mutate(null)
  }

  return (
    <section className="cliente-venta" aria-label="Cliente de la venta">
      <h3>
        Cliente <kbd>F6</kbd>
      </h3>
      <p>
        <strong>{describirCliente(data?.cliente ?? null)}</strong>
      </p>
      <form
        className="en-linea"
        onSubmit={(evento) => {
          evento.preventDefault()
          buscar()
        }}
      >
        <label>
          NIT
          <input
            name="nit"
            autoComplete="off"
            maxLength={20}
            placeholder="1234567-8"
            value={nit}
            onChange={(evento) => setNit(evento.target.value)}
          />
        </label>
        <button type="submit" disabled={consulta.isPending || !nit.trim()}>
          {consulta.isPending ? 'Buscando…' : 'Buscar'}
        </button>
        <button type="button" data-control="consumidor-final" onClick={aConsumidorFinal}>
          CF
        </button>
      </form>
      {problema && (
        <p className="error" role="alert">
          {problema}
        </p>
      )}
      {(consulta.error ?? asignacion.error) && (
        <p className="error" role="alert">
          {(consulta.error ?? asignacion.error)?.message}
        </p>
      )}
      {busqueda && (
        <p className={busqueda.cliente ? 'exito' : 'pendiente'} role="status">
          {avisoBusqueda(busqueda)}
        </p>
      )}
      {busqueda && !busqueda.cliente && (
        <RegistroCliente
          key={busqueda.nit}
          nit={busqueda.nit}
          onRegistrado={(cliente) => {
            setBusqueda(undefined)
            asignacion.mutate(cliente)
          }}
          onConsumidorFinal={aConsumidorFinal}
        />
      )}
    </section>
  )
}

/** HU-05.04 crit. 3 y HU-05.05: el nombre escrito a mano cuando el certificador no lo dio. */
export function RegistroCliente({
  nit,
  onRegistrado,
  onConsumidorFinal,
}: {
  nit: string
  onRegistrado: (cliente: Cliente) => void
  onConsumidorFinal: () => void
}) {
  const [nombre, setNombre] = useState('')
  const [direccion, setDireccion] = useState('')
  const [correo, setCorreo] = useState('')
  const registro = useMutation({
    mutationFn: () => registrarCliente({ nit: normalizarNit(nit), nombre, direccion, correo }),
    onSuccess: onRegistrado,
  })

  return (
    <form
      className="formulario"
      aria-label="Registrar cliente"
      onSubmit={(evento) => {
        evento.preventDefault()
        registro.mutate()
      }}
    >
      <label>
        Nombre del cliente
        <input name="nombreCliente" required maxLength={200} value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </label>
      <label>
        Dirección (opcional)
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
      {registro.error && (
        <p className="error" role="alert">
          {registro.error.message}
        </p>
      )}
      <div className="acciones">
        <button type="submit" disabled={registro.isPending}>
          {registro.isPending ? 'Registrando…' : 'Registrar y usar'}
        </button>
        <button type="button" onClick={onConsumidorFinal}>
          Facturar como CF
        </button>
      </div>
    </form>
  )
}
