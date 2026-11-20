import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CLAVE_SERVIDOR_CORREO,
  guardarServidorCorreo,
  obtenerServidorCorreo,
  probarServidorCorreo,
  PUERTO_USUAL,
  type DatosServidorCorreo,
  type SeguridadCorreo,
  type ServidorCorreo,
} from '../../api/servidorCorreo'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { segundosAMs, segundosValidos } from './textos'

const SEGURIDADES: { valor: SeguridadCorreo; etiqueta: string }[] = [
  { valor: 'STARTTLS', etiqueta: 'STARTTLS (puerto 587, lo usual)' },
  { valor: 'SSL', etiqueta: 'SSL (puerto 465)' },
  { valor: 'NINGUNA', etiqueta: 'Ninguna (solo un servidor dentro de la red)' },
]

/**
 * HU-08.15: el servidor de correo con que el POS envía las facturas (decisión de Sergio: lo envía el POS, no el
 * certificador). Solo para quien puede modificar la configuración; el servidor también lo exige.
 */
export function PaginaServidorCorreo() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Servidor de correo</h1>
      <NavConfiguracion actual="Correo" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaServidorCorreo />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaServidorCorreo() {
  const { data, error, isPending } = useQuery({ queryKey: CLAVE_SERVIDOR_CORREO, queryFn: obtenerServidorCorreo })

  if (isPending) return <p className="tarjeta">Cargando el servidor de correo…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioServidorCorreo guardado={data} />
}

/**
 * El servidor SMTP, el remitente y el tiempo límite. La contraseña guardada nunca se muestra: el campo queda vacío y
 * se puede dejar así para conservarla. "Enviar prueba" usa los datos del formulario sin guardarlos.
 */
export function FormularioServidorCorreo({ guardado }: { guardado: ServidorCorreo }) {
  const clienteConsultas = useQueryClient()
  const [servidor, setServidor] = useState(guardado.servidor ?? '')
  const [seguridad, setSeguridad] = useState<SeguridadCorreo>(guardado.seguridad ?? 'STARTTLS')
  const [puerto, setPuerto] = useState(String(guardado.puerto ?? PUERTO_USUAL.STARTTLS))
  const [usuario, setUsuario] = useState(guardado.usuario ?? '')
  const [secreto, setSecreto] = useState('')
  const [remitente, setRemitente] = useState(guardado.remitente ?? '')
  const [nombreRemitente, setNombreRemitente] = useState(guardado.nombreRemitente ?? '')
  const [segundos, setSegundos] = useState(String((guardado.timeoutMs ?? 10000) / 1000))
  const [destinatario, setDestinatario] = useState('')
  const [aviso, setAviso] = useState<string>()

  const datos = (): DatosServidorCorreo => ({
    servidor,
    puerto: Number(puerto),
    seguridad,
    usuario,
    secreto,
    remitente,
    nombreRemitente,
    timeoutMs: segundosAMs(segundos),
  })
  const problema = !/^\d{1,5}$/.test(puerto)
    ? 'El puerto debe ser un número de 1 a 65535.'
    : !segundosValidos(segundos)
      ? 'El tiempo límite debe ser de 1 a 60 segundos, sin decimales.'
      : undefined
  const conServidorGuardado = guardado.secretoGuardado && usuario === (guardado.usuario ?? '')

  const prueba = useMutation({
    mutationFn: () => probarServidorCorreo(datos(), destinatario),
    onMutate: () => setAviso(undefined),
  })
  const guardar = useMutation({
    mutationFn: () => guardarServidorCorreo(datos()),
    onMutate: () => setAviso(undefined),
    onSuccess: (actualizado) => {
      clienteConsultas.setQueryData(CLAVE_SERVIDOR_CORREO, actualizado)
      setSecreto('')
      prueba.reset()
      setAviso('Servidor de correo guardado. Las facturas de los clientes con correo se envían con él.')
    },
  })

  return (
    <form
      className="tarjeta formulario"
      aria-label="Servidor de correo"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (!problema) guardar.mutate()
      }}
    >
      {!guardado.configurado && (
        <p className="pendiente">
          Sin servidor configurado, las facturas de los clientes con correo esperan y se envían cuando se configure.
        </p>
      )}
      {aviso && (
        <p className="exito" role="status">
          {aviso}
        </p>
      )}
      <label>
        Servidor SMTP
        <input
          name="servidor"
          required
          maxLength={255}
          placeholder="smtp.correo.com"
          value={servidor}
          onChange={(evento) => setServidor(evento.target.value)}
        />
      </label>
      <fieldset>
        <legend>Seguridad de la conexión</legend>
        {SEGURIDADES.map((opcion) => (
          <label key={opcion.valor}>
            <input
              type="radio"
              name="seguridad"
              value={opcion.valor}
              checked={seguridad === opcion.valor}
              onChange={() => {
                setSeguridad(opcion.valor)
                setPuerto(String(PUERTO_USUAL[opcion.valor]))
              }}
            />{' '}
            {opcion.etiqueta}
          </label>
        ))}
      </fieldset>
      <label>
        Puerto
        <input name="puerto" inputMode="numeric" value={puerto} onChange={(evento) => setPuerto(evento.target.value)} />
      </label>
      <label>
        Usuario (opcional)
        <input
          name="usuario"
          maxLength={254}
          autoComplete="off"
          value={usuario}
          onChange={(evento) => setUsuario(evento.target.value)}
        />
        <small>Déjelo vacío si el servidor no pide usuario ni contraseña.</small>
      </label>
      {usuario.trim() !== '' && (
        <label>
          Contraseña
          <input
            name="secreto"
            type="password"
            autoComplete="new-password"
            required={!conServidorGuardado}
            placeholder={conServidorGuardado ? '••••••••' : ''}
            value={secreto}
            onChange={(evento) => setSecreto(evento.target.value)}
          />
          <small>
            {conServidorGuardado
              ? 'Hay una contraseña guardada (cifrada). Déjelo vacío para conservarla.'
              : 'Se guarda cifrada y no se vuelve a mostrar.'}
          </small>
        </label>
      )}
      <label>
        Correo del remitente
        <input
          name="remitente"
          type="email"
          required
          maxLength={254}
          placeholder="facturas@supermercado.com"
          value={remitente}
          onChange={(evento) => setRemitente(evento.target.value)}
        />
      </label>
      <label>
        Nombre del remitente (opcional)
        <input
          name="nombreRemitente"
          maxLength={100}
          placeholder="Supermercado"
          value={nombreRemitente}
          onChange={(evento) => setNombreRemitente(evento.target.value)}
        />
      </label>
      <label>
        Tiempo límite (segundos)
        <input
          name="segundos"
          inputMode="numeric"
          value={segundos}
          onChange={(evento) => setSegundos(evento.target.value)}
        />
        <small>Si el servidor no responde en este tiempo, el envío se reintenta más tarde.</small>
      </label>
      <label>
        Enviar la prueba a
        <input
          name="destinatario"
          type="email"
          maxLength={254}
          placeholder="su.correo@supermercado.com"
          value={destinatario}
          onChange={(evento) => setDestinatario(evento.target.value)}
        />
      </label>

      {prueba.data && (
        <p className={prueba.data.exitosa ? 'exito' : 'error'} role="status" data-resultado="prueba-correo">
          {prueba.data.mensaje}
        </p>
      )}
      {(problema || prueba.error || guardar.error) && (
        <p className="error" role="alert">
          {problema ?? prueba.error?.message ?? guardar.error?.message}
        </p>
      )}
      <div className="acciones">
        <button
          type="button"
          data-control="probar-correo"
          disabled={prueba.isPending || problema !== undefined || destinatario.trim() === ''}
          onClick={() => prueba.mutate()}
        >
          {prueba.isPending ? 'Enviando…' : 'Enviar prueba'}
        </button>
        <button type="submit" disabled={guardar.isPending}>
          {guardar.isPending ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
      {guardado.modificadoEn && (
        <p className="pendiente">Última modificación: {new Date(guardado.modificadoEn).toLocaleString('es-GT')}.</p>
      )}
    </form>
  )
}
