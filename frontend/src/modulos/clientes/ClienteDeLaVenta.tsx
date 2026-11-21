import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  asignarClienteAVenta,
  buscarClientePorCui,
  buscarClientePorNit,
  cambiarCorreoDeFactura,
  claveClienteDeVenta,
  obtenerClienteDeVenta,
  registrarCliente,
  type BusquedaNit,
  type Cliente,
  type ClienteDeVenta,
  type TipoIdentificacion,
} from '../../api/clientes'
import { EdicionCliente } from './EdicionCliente'
import { nitValido, normalizarNit } from './nit'
import {
  avisoBusqueda,
  avisoCorreoFactura,
  CORREO_INVALIDO,
  correoValido,
  CUI_INVALIDO,
  cuiValido,
  normalizarCui,
  describirCliente,
  NIT_CLIENTE_INVALIDO,
} from './textos'

/**
 * EP-05: el cliente de la venta en curso.
 * - HU-05.01: cada venta empieza a Consumidor Final; volver a CF es un solo clic.
 * - HU-05.02: con el NIT (con o sin guion) se cargan el nombre y la dirección del cliente registrado.
 * - HU-05.03: un NIT mal escrito se avisa y no se usa.
 * - HU-05.04: un NIT nuevo se consulta al certificador; si no aparece o no responde, se escribe el nombre o se usa CF.
 * - HU-05.05: el cliente nuevo se registra sin salir de la venta (dirección "Ciudad" si se deja vacía).
 * - HU-05.06: sin NIT, el cliente se busca y se registra con su CUI (13 dígitos), elegido en el selector.
 * - HU-05.08: el cliente se cambia hasta finalizar el cobro; si el cambio impide cobrar (límite de CF), se avisa.
 * - HU-05.09: los datos del cliente asignado (nombre, dirección y correo) se corrigen sin salir de la venta.
 * - HU-05.11: el correo al que se envía la factura (HU-08.15), opcional.
 * F6 lleva al campo del NIT (HU-06.17).
 */
export function ClienteDeLaVenta({ ventaId }: { ventaId: number }) {
  const clave = claveClienteDeVenta(ventaId)
  const clienteConsultas = useQueryClient()
  const { data } = useQuery({ queryKey: clave, queryFn: obtenerClienteDeVenta })
  const [tipo, setTipo] = useState<TipoIdentificacion>('NIT')
  const [nit, setNit] = useState('')
  const [problema, setProblema] = useState<string>()
  const [busqueda, setBusqueda] = useState<BusquedaNit>()
  const [editando, setEditando] = useState(false)

  const asignacion = useMutation({
    mutationFn: (cliente: Cliente | null) => asignarClienteAVenta(cliente?.id ?? null),
    onSuccess: (actual) => clienteConsultas.setQueryData(clave, actual),
  })
  const consulta = useMutation({
    mutationFn: () => (tipo === 'CUI' ? buscarClientePorCui(nit) : buscarClientePorNit(nit)),
    onMutate: () => setBusqueda(undefined),
    onSuccess: (resultado) => {
      setBusqueda(resultado)
      if (resultado.cliente) asignacion.mutate(resultado.cliente)
    },
  })

  const buscar = () => {
    asignacion.reset()
    if (tipo === 'CUI' ? !cuiValido(nit) : !nitValido(nit)) {
      setProblema(tipo === 'CUI' ? CUI_INVALIDO : NIT_CLIENTE_INVALIDO)
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
        {data?.cliente && !editando && (
          <>
            {' '}
            <button type="button" data-control="editar-cliente" onClick={() => setEditando(true)}>
              Editar datos
            </button>
          </>
        )}
      </p>
      {/* HU-05.08 crit. 1: el cambio se acepta, pero se avisa si con este cliente no se podrá cobrar. */}
      {data?.aviso && (
        <p className="pendiente" role="alert">
          {data.aviso}
        </p>
      )}
      {/* HU-05.09 crit. 1: los datos del cliente se corrigen sin salir de la venta. */}
      {data?.cliente && editando && (
        <EdicionCliente
          cliente={data.cliente}
          onCancelar={() => setEditando(false)}
          onGuardado={(cliente) => {
            setEditando(false)
            clienteConsultas.setQueryData(clave, { ...data, cliente })
            void clienteConsultas.invalidateQueries({ queryKey: ['clientes', 'lista'] })
          }}
        />
      )}
      {data && (
        <CorreoDeFactura
          key={data.correoFactura ?? ''}
          correo={data.correoFactura}
          onCambiado={(actual) => clienteConsultas.setQueryData(clave, actual)}
        />
      )}
      <form
        className="en-linea"
        onSubmit={(evento) => {
          evento.preventDefault()
          buscar()
        }}
      >
        <label>
          Identificación
          <select
            name="tipoIdentificacion"
            value={tipo}
            onChange={(evento) => {
              setTipo(evento.target.value as TipoIdentificacion)
              setProblema(undefined)
              setBusqueda(undefined)
            }}
          >
            <option value="NIT">NIT</option>
            <option value="CUI">CUI</option>
          </select>
        </label>
        <label>
          {tipo}
          <input
            name="nit"
            autoComplete="off"
            maxLength={20}
            placeholder={tipo === 'CUI' ? '1234 56789 0101' : '1234567-8'}
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
          key={`${busqueda.tipoIdentificacion}-${busqueda.nit}`}
          nit={busqueda.nit}
          tipo={busqueda.tipoIdentificacion}
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

/**
 * HU-05.11: crit. 1, el correo se agrega o se cambia durante la venta; crit. 2, es opcional y no se insiste; crit. 3,
 * se valida su forma; crit. 4, si hay correo se avisa que la factura se enviará, con la opción de no enviarla.
 */
export function CorreoDeFactura({
  correo,
  onCambiado,
}: {
  correo: string | null
  onCambiado: (actual: ClienteDeVenta) => void
}) {
  const [editando, setEditando] = useState(false)
  const [escrito, setEscrito] = useState(correo ?? '')
  const [problema, setProblema] = useState<string>()
  const cambio = useMutation({
    mutationFn: cambiarCorreoDeFactura,
    onSuccess: (actual) => {
      setEditando(false)
      onCambiado(actual)
    },
  })

  if (!editando)
    return (
      <p className="correo-factura">
        <span role="status">{avisoCorreoFactura(correo)}</span>{' '}
        <button type="button" data-control="editar-correo" onClick={() => setEditando(true)}>
          {correo ? 'Cambiar correo' : 'Agregar correo'}
        </button>
        {correo && (
          <button
            type="button"
            data-control="no-enviar-correo"
            disabled={cambio.isPending}
            onClick={() => cambio.mutate('')}
          >
            No enviar
          </button>
        )}
      </p>
    )
  return (
    <form
      className="en-linea"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (escrito.trim() !== '' && !correoValido(escrito)) {
          setProblema(CORREO_INVALIDO)
          return
        }
        setProblema(undefined)
        cambio.mutate(escrito)
      }}
    >
      <label>
        Correo para la factura
        <input
          name="correoFactura"
          type="email"
          maxLength={254}
          autoComplete="off"
          placeholder="cliente@correo.com"
          value={escrito}
          onChange={(evento) => setEscrito(evento.target.value)}
        />
      </label>
      <button type="submit" disabled={cambio.isPending}>
        {cambio.isPending ? 'Guardando…' : 'Guardar'}
      </button>
      <button type="button" onClick={() => setEditando(false)}>
        Cancelar
      </button>
      {(problema ?? cambio.error) && (
        <p className="error" role="alert">
          {problema ?? cambio.error?.message}
        </p>
      )}
    </form>
  )
}

/** HU-05.04 crit. 3 y HU-05.05: el nombre escrito a mano cuando el certificador no lo dio. */
export function RegistroCliente({
  nit,
  tipo = 'NIT',
  onRegistrado,
  onConsumidorFinal,
}: {
  nit: string
  tipo?: TipoIdentificacion
  onRegistrado: (cliente: Cliente) => void
  onConsumidorFinal: () => void
}) {
  const [nombre, setNombre] = useState('')
  const [direccion, setDireccion] = useState('')
  const [correo, setCorreo] = useState('')
  const registro = useMutation({
    mutationFn: () =>
      registrarCliente({
        tipoIdentificacion: tipo,
        nit: tipo === 'CUI' ? normalizarCui(nit) : normalizarNit(nit),
        nombre,
        direccion,
        correo,
      }),
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
