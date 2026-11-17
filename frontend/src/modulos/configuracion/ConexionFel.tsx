import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CLAVE_CONEXION_FEL,
  guardarConexionFel,
  obtenerConexionFel,
  probarConexionFel,
  type AmbienteFel,
  type ConexionFel,
  type DatosConexionFel,
} from '../../api/conexionFel'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { avisoConexionGuardada, CONFIRMACION_PRODUCCION, segundosAMs, segundosValidos } from './textos'

/**
 * HU-08.01, HU-12.03 y HU-12.15: la conexión con el certificador FEL. Solo para quien puede modificar la configuración
 * (el Administrador, HU-12.15 crit. 3); el servidor también lo exige.
 */
export function PaginaConexionFel() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Conexión FEL</h1>
      <NavConfiguracion actual="Conexión FEL" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaConexionFel />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaConexionFel() {
  const { data: conexion, error, isPending } = useQuery({ queryKey: CLAVE_CONEXION_FEL, queryFn: obtenerConexionFel })

  if (isPending) return <p className="tarjeta">Cargando la conexión…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <FormularioConexionFel guardada={conexion} />
}

/**
 * - HU-12.03 crit. 1 y 4: certificador, dirección del servicio, usuario, llave o token, ambiente y tiempo límite.
 * - HU-08.01 crit. 2: la dirección debe ser https.
 * - HU-12.03 crit. 2 y HU-08.01 crit. 3: "Probar conexión" usa los datos del formulario, sin guardarlos ni emitir
 *   facturas.
 * - HU-12.03 crit. 3: pasar a producción pide una confirmación explícita.
 * - HU-12.15 crit. 2: la llave guardada nunca se muestra; el campo queda vacío y se puede dejar así para conservarla.
 */
export function FormularioConexionFel({ guardada }: { guardada: ConexionFel }) {
  const clienteConsultas = useQueryClient()
  const [certificador, setCertificador] = useState(guardada.certificador ?? '')
  const [nitCertificador, setNitCertificador] = useState(guardada.nitCertificador ?? '')
  const [urlServicio, setUrlServicio] = useState(guardada.urlServicio ?? 'https://')
  const [usuario, setUsuario] = useState(guardada.usuario ?? '')
  const [secreto, setSecreto] = useState('')
  const [ambiente, setAmbiente] = useState<AmbienteFel>(guardada.ambiente ?? 'PRUEBAS')
  const [segundos, setSegundos] = useState(String((guardada.timeoutMs ?? 8000) / 1000))
  const [aviso, setAviso] = useState<string>()

  const datos = (confirmarProduccion?: boolean): DatosConexionFel => ({
    certificador,
    nitCertificador,
    urlServicio,
    usuario,
    secreto,
    ambiente,
    timeoutMs: segundosAMs(segundos),
    confirmarProduccion,
  })
  const problema = !segundosValidos(segundos) ? 'El tiempo límite debe ser de 1 a 60 segundos, sin decimales.' : undefined

  const prueba = useMutation({ mutationFn: () => probarConexionFel(datos()), onMutate: () => setAviso(undefined) })
  const guardado = useMutation({
    mutationFn: (confirmarProduccion?: boolean) => guardarConexionFel(datos(confirmarProduccion)),
    onMutate: () => setAviso(undefined),
    onSuccess: (actualizada) => {
      clienteConsultas.setQueryData(CLAVE_CONEXION_FEL, actualizada)
      setSecreto('')
      prueba.reset()
      setAviso(avisoConexionGuardada(actualizada.ambiente ?? ambiente))
    },
  })
  const pasaAProduccion = ambiente === 'PRODUCCION' && guardada.ambiente !== 'PRODUCCION'

  return (
    <form
      className="tarjeta formulario"
      aria-label="Conexión con el certificador FEL"
      onSubmit={(evento) => {
        evento.preventDefault()
        if (problema) return
        if (pasaAProduccion && !window.confirm(CONFIRMACION_PRODUCCION)) return
        guardado.mutate(pasaAProduccion || undefined)
      }}
    >
      {aviso && (
        <p className="exito" role="status">
          {aviso}
        </p>
      )}
      <label>
        Certificador
        <input
          name="certificador"
          required
          maxLength={60}
          placeholder="Nombre del certificador contratado"
          value={certificador}
          onChange={(evento) => setCertificador(evento.target.value)}
        />
      </label>
      <label>
        NIT del certificador (opcional)
        <input
          name="nitCertificador"
          maxLength={20}
          autoComplete="off"
          value={nitCertificador}
          onChange={(evento) => setNitCertificador(evento.target.value)}
        />
        <small>Aparece en la factura impresa junto al nombre del certificador.</small>
      </label>
      <label>
        Dirección del servicio
        <input
          name="urlServicio"
          type="url"
          required
          pattern="https://.+"
          title="Debe empezar con https:// (canal cifrado)"
          value={urlServicio}
          onChange={(evento) => setUrlServicio(evento.target.value)}
        />
        <small>Solo https://: la comunicación con el certificador va cifrada.</small>
      </label>
      <label>
        Usuario
        <input
          name="usuario"
          required
          maxLength={100}
          autoComplete="off"
          value={usuario}
          onChange={(evento) => setUsuario(evento.target.value)}
        />
      </label>
      <label>
        Llave o token
        <input
          name="secreto"
          type="password"
          autoComplete="new-password"
          required={!guardada.secretoGuardado}
          placeholder={guardada.secretoGuardado ? '••••••••' : ''}
          value={secreto}
          onChange={(evento) => setSecreto(evento.target.value)}
        />
        <small>
          {guardada.secretoGuardado
            ? 'Hay una llave guardada (cifrada). Déjelo vacío para conservarla.'
            : 'Se guarda cifrada y no se vuelve a mostrar.'}
        </small>
      </label>
      <fieldset>
        <legend>Ambiente</legend>
        <label>
          <input
            type="radio"
            name="ambiente"
            value="PRUEBAS"
            checked={ambiente === 'PRUEBAS'}
            onChange={() => setAmbiente('PRUEBAS')}
          />{' '}
          Pruebas (no emite facturas reales)
        </label>
        <label>
          <input
            type="radio"
            name="ambiente"
            value="PRODUCCION"
            checked={ambiente === 'PRODUCCION'}
            onChange={() => setAmbiente('PRODUCCION')}
          />{' '}
          Producción
        </label>
      </fieldset>
      <label>
        Tiempo límite de respuesta (segundos)
        <input
          name="segundos"
          inputMode="numeric"
          value={segundos}
          onChange={(evento) => setSegundos(evento.target.value)}
        />
        <small>Si el certificador no responde en este tiempo, la factura pasa a contingencia.</small>
      </label>

      {prueba.data && (
        <p className={prueba.data.exitosa ? 'exito' : 'error'} role="status" data-resultado="prueba-conexion">
          {prueba.data.mensaje}
        </p>
      )}
      {(problema || prueba.error || guardado.error) && (
        <p className="error" role="alert">
          {problema ?? prueba.error?.message ?? guardado.error?.message}
        </p>
      )}
      <div className="acciones">
        <button
          type="button"
          data-control="probar-conexion"
          disabled={prueba.isPending || problema !== undefined}
          onClick={() => prueba.mutate()}
        >
          {prueba.isPending ? 'Probando…' : 'Probar conexión'}
        </button>
        <button type="submit" disabled={guardado.isPending}>
          {guardado.isPending ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
      {guardada.modificadoEn && (
        <p className="pendiente">Última modificación: {new Date(guardada.modificadoEn).toLocaleString('es-GT')}.</p>
      )}
    </form>
  )
}
