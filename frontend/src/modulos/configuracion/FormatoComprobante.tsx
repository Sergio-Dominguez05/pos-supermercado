import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  BYTES_MAXIMOS_LOGO,
  CLAVE_FORMATO_COMPROBANTE,
  guardarFormatoComprobante,
  LARGO_MAXIMO_ENCABEZADO,
  LARGO_MAXIMO_PIE,
  obtenerFormatoComprobante,
  vistaPreviaComprobante,
  type FormatoComprobante,
  type SolicitudFormato,
  type VistaPrevia,
} from '../../api/comprobante'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'

/** HU-12.09 Personalizar el formato del comprobante (solo con el permiso de modificar la configuración). */
export function PaginaFormatoComprobante() {
  const { data: usuario } = useSesion()
  if (!usuario) return null
  return (
    <main className="pagina pagina-ancha">
      <h1>Formato del comprobante</h1>
      <NavConfiguracion actual="Comprobante" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaFormato />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaFormato() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_FORMATO_COMPROBANTE,
    queryFn: obtenerFormatoComprobante,
  })
  if (isPending) return <p className="tarjeta">Cargando el formato…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <EdicionFormato guardado={data} />
}

/** Lee la imagen elegida como `data:` URL, para enviarla en Base64. */
const leerImagen = (archivo: File) =>
  new Promise<string>((resolver, rechazar) => {
    const lector = new FileReader()
    lector.onload = () => resolver(String(lector.result))
    lector.onerror = () => rechazar(new Error('No se pudo leer la imagen.'))
    lector.readAsDataURL(archivo)
  })

/**
 * Crit. 1 a 3: el logo, los mensajes y la vista previa. Para guardar hay que ver antes la vista previa de lo que se
 * guardará (crit. 3); cualquier cambio pide verla otra vez.
 */
export function EdicionFormato({ guardado }: { guardado: FormatoComprobante }) {
  const consultas = useQueryClient()
  const [encabezado, setEncabezado] = useState(guardado.encabezado ?? '')
  const [pie, setPie] = useState(guardado.pie ?? '')
  const [logo, setLogo] = useState<string | null>(null)
  const [quitarLogo, setQuitarLogo] = useState(false)
  const [anchoMm, setAnchoMm] = useState<58 | 80>(80)
  const [problema, setProblema] = useState<string>()
  const [aviso, setAviso] = useState<string>()
  const solicitud: SolicitudFormato = { encabezado, pie, logo, quitarLogo }

  const vista = useMutation({ mutationFn: () => vistaPreviaComprobante(solicitud, anchoMm) })
  const guardar = useMutation({
    mutationFn: () => guardarFormatoComprobante(solicitud),
    onSuccess: (actual) => {
      consultas.setQueryData(CLAVE_FORMATO_COMPROBANTE, actual)
      setLogo(null)
      setQuitarLogo(false)
      vista.reset()
      setAviso('El formato se guardó. Las próximas facturas saldrán así.')
    },
  })
  const cambio = <T,>(asignar: (valor: T) => void) => (valor: T) => {
    asignar(valor)
    vista.reset()
    setAviso(undefined)
  }

  return (
    <div className="dos-columnas">
      <form
        className="tarjeta formulario"
        aria-label="Formato del comprobante"
        onSubmit={(evento) => {
          evento.preventDefault()
          vista.mutate()
        }}
      >
        <p>
          Puede agregar el logo y mensajes. Los datos que exige la SAT y los totales (Subtotal, IVA y TOTAL) siempre se
          imprimen igual y no se pueden quitar.
        </p>
        <label>
          Logo (se imprime en blanco y negro)
          <input
            name="logo"
            type="file"
            accept="image/png,image/jpeg,image/gif,image/bmp"
            onChange={async (evento) => {
              const archivo = evento.target.files?.[0]
              if (!archivo) return
              if (archivo.size > BYTES_MAXIMOS_LOGO) {
                setProblema('El logo puede pesar como máximo 1 MB.')
                return
              }
              setProblema(undefined)
              cambio(setLogo)(await leerImagen(archivo))
              setQuitarLogo(false)
            }}
          />
        </label>
        {guardado.logo && !logo && (
          <label className="casilla">
            <input
              type="checkbox"
              name="quitarLogo"
              checked={quitarLogo}
              onChange={(evento) => cambio(setQuitarLogo)(evento.target.checked)}
            />
            Quitar el logo actual
          </label>
        )}
        <label>
          Mensaje del encabezado (opcional)
          <input
            name="encabezado"
            maxLength={LARGO_MAXIMO_ENCABEZADO}
            placeholder="Precios bajos todos los días"
            value={encabezado}
            onChange={(evento) => cambio(setEncabezado)(evento.target.value)}
          />
        </label>
        <label>
          Mensaje del pie (opcional)
          <textarea
            name="pie"
            maxLength={LARGO_MAXIMO_PIE}
            rows={2}
            placeholder="¡Gracias por su compra!"
            value={pie}
            onChange={(evento) => cambio(setPie)(evento.target.value)}
          />
        </label>
        <label>
          Ver en papel de
          <select
            name="anchoMm"
            value={anchoMm}
            onChange={(evento) => cambio(setAnchoMm)(Number(evento.target.value) as 58 | 80)}
          >
            <option value={80}>80 mm</option>
            <option value={58}>58 mm</option>
          </select>
        </label>
        {(problema ?? vista.error ?? guardar.error) && (
          <p className="error" role="alert">
            {problema ?? (vista.error ?? guardar.error)?.message}
          </p>
        )}
        {aviso && (
          <p className="exito" role="status">
            {aviso}
          </p>
        )}
        <div className="acciones">
          <button type="submit" disabled={vista.isPending}>
            {vista.isPending ? 'Preparando…' : 'Vista previa'}
          </button>
          <button
            type="button"
            className="boton"
            disabled={!vista.data || guardar.isPending}
            title={vista.data ? undefined : 'Vea primero la vista previa'}
            onClick={() => guardar.mutate()}
          >
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
      {vista.data ? (
        <TicketDeMuestra vista={vista.data} />
      ) : (
        <p className="tarjeta">Use “Vista previa” para ver cómo saldrá la factura antes de guardar.</p>
      )}
    </div>
  )
}

/** Crit. 3: el ticket como saldría en la impresora, con su logo y el ancho del papel. */
export function TicketDeMuestra({ vista }: { vista: VistaPrevia }) {
  return (
    <figure className="tarjeta ticket-muestra" aria-label="Vista previa del comprobante">
      <figcaption>Vista previa (factura de ejemplo, {vista.columnas} columnas)</figcaption>
      <div className="papel" style={{ width: `${vista.columnas + 2}ch` }}>
        {vista.logo && <img src={vista.logo} alt="Logo en blanco y negro" />}
        {vista.renglones.map((renglon, indice) => (
          <div
            key={indice}
            className={[
              renglon.alineacion === 'CENTRO' ? 'centro' : '',
              renglon.negrita ? 'negrita' : '',
              renglon.destacado ? 'destacado' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            {renglon.texto || ' '}
          </div>
        ))}
      </div>
    </figure>
  )
}
