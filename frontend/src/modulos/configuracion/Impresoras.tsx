import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CLAVE_IMPRESORAS,
  crearImpresora,
  editarImpresora,
  listarImpresoras,
  probarImpresora,
  type Impresora,
  type ResultadoPrueba,
} from '../../api/impresoras'
import { useSesion } from '../login/sesion'
import { NavConfiguracion } from './NavConfiguracion'
import { avisoImpresoraGuardada } from './textos'

/** HU-12.08: las impresoras térmicas de la red. Solo para quien puede modificar la configuración. */
export function PaginaImpresoras() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Impresoras</h1>
      <NavConfiguracion actual="Impresoras" />
      {usuario.permisos.includes('CONFIGURACION_MODIFICAR') ? (
        <CargaImpresoras />
      ) : (
        <p className="tarjeta error">No tiene permiso para modificar la configuración.</p>
      )}
    </main>
  )
}

function CargaImpresoras() {
  const { data, error, isPending } = useQuery({ queryKey: CLAVE_IMPRESORAS, queryFn: listarImpresoras })

  if (isPending) return <p className="tarjeta">Cargando impresoras…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <GestionImpresoras impresoras={data} />
}

/**
 * - Crit. 1: dirección de la impresora en la red y ancho de papel (58 u 80 mm). Cada caja elige la suya en Cajas.
 * - Crit. 2: página de prueba de cada impresora.
 * - Crit. 3: copias de cada factura y de cada cierre de caja.
 */
export function GestionImpresoras({ impresoras }: { impresoras: Impresora[] }) {
  const [editando, setEditando] = useState<Impresora | null>(null)
  const [aviso, setAviso] = useState<string>()

  return (
    <>
      {aviso && (
        <p className="tarjeta exito" role="status">
          {aviso}
        </p>
      )}
      {impresoras.length === 0 ? (
        <p className="tarjeta">Todavía no hay impresoras. Agregue la primera con el formulario.</p>
      ) : (
        <table className="tabla">
          <thead>
            <tr>
              <th>Impresora</th>
              <th>Dirección</th>
              <th>Papel</th>
              <th>Copias</th>
              <th>
                <span className="solo-lector">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {impresoras.map((impresora) => (
              <FilaImpresora
                key={impresora.id}
                impresora={impresora}
                onEditar={() => {
                  setAviso(undefined)
                  setEditando(impresora)
                }}
              />
            ))}
          </tbody>
        </table>
      )}
      <FormularioImpresora
        key={editando?.id ?? 'nueva'}
        impresora={editando ?? undefined}
        onGuardada={(guardada, nueva) => {
          setEditando(null)
          setAviso(avisoImpresoraGuardada(guardada, nueva))
        }}
        onCancelar={() => setEditando(null)}
      />
    </>
  )
}

function FilaImpresora({ impresora, onEditar }: { impresora: Impresora; onEditar: () => void }) {
  const prueba = useMutation({ mutationFn: () => probarImpresora(impresora.id) })

  return (
    <tr>
      <td>{impresora.nombre}</td>
      <td>
        {impresora.host}:{impresora.puerto}
      </td>
      <td>{impresora.anchoMm} mm</td>
      <td>
        Factura {impresora.copiasFactura} · Cierre {impresora.copiasCierre}
      </td>
      <td className="acciones">
        <button type="button" onClick={onEditar} aria-label={`Editar ${impresora.nombre}`}>
          Editar
        </button>
        <button
          type="button"
          onClick={() => prueba.mutate()}
          disabled={prueba.isPending}
          aria-label={`Imprimir página de prueba en ${impresora.nombre}`}
        >
          {prueba.isPending ? 'Imprimiendo…' : 'Página de prueba'}
        </button>
        {prueba.data && <ResultadoDePrueba resultado={prueba.data} />}
        {prueba.error && (
          <p className="error" role="alert">
            {prueba.error.message}
          </p>
        )}
      </td>
    </tr>
  )
}

export function ResultadoDePrueba({ resultado }: { resultado: ResultadoPrueba }) {
  return (
    <p className={resultado.exitosa ? 'exito' : 'error'} role={resultado.exitosa ? 'status' : 'alert'}>
      {resultado.mensaje}
    </p>
  )
}

export function FormularioImpresora({
  impresora,
  onGuardada,
  onCancelar,
}: {
  impresora?: Impresora
  onGuardada: (impresora: Impresora, nueva: boolean) => void
  onCancelar: () => void
}) {
  const clienteConsultas = useQueryClient()
  const [nombre, setNombre] = useState(impresora?.nombre ?? '')
  const [host, setHost] = useState(impresora?.host ?? '')
  const [puerto, setPuerto] = useState(String(impresora?.puerto ?? 9100))
  const [anchoMm, setAnchoMm] = useState<58 | 80>(impresora?.anchoMm ?? 80)
  const [copiasFactura, setCopiasFactura] = useState(String(impresora?.copiasFactura ?? 1))
  const [copiasCierre, setCopiasCierre] = useState(String(impresora?.copiasCierre ?? 1))

  const guardado = useMutation({
    mutationFn: () => {
      const datos = {
        nombre,
        host,
        puerto: Number(puerto),
        anchoMm,
        copiasFactura: Number(copiasFactura),
        copiasCierre: Number(copiasCierre),
      }
      return impresora ? editarImpresora(impresora.id, datos) : crearImpresora(datos)
    },
    onSuccess: async (guardada) => {
      await clienteConsultas.invalidateQueries({ queryKey: CLAVE_IMPRESORAS })
      onGuardada(guardada, !impresora)
    },
  })

  return (
    <form
      className="tarjeta formulario"
      aria-label={impresora ? `Editar ${impresora.nombre}` : 'Nueva impresora'}
      onSubmit={(evento) => {
        evento.preventDefault()
        guardado.mutate()
      }}
    >
      <h2>{impresora ? `Editar ${impresora.nombre}` : 'Nueva impresora'}</h2>
      <label>
        Nombre
        <input
          name="nombre"
          required
          maxLength={60}
          placeholder="Térmica caja 1"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
        />
      </label>
      <label>
        Dirección en la red
        <input
          name="host"
          required
          maxLength={253}
          pattern="[A-Za-z0-9]([A-Za-z0-9.\-]*[A-Za-z0-9])?"
          title="La dirección IP o el nombre de la impresora, sin http:// ni puerto"
          placeholder="192.168.1.50"
          autoComplete="off"
          value={host}
          onChange={(evento) => setHost(evento.target.value.trim())}
        />
        <small>La impresora debe tener una dirección fija en la red del supermercado.</small>
      </label>
      <label>
        Puerto
        <input
          name="puerto"
          type="number"
          required
          min={1}
          max={65535}
          value={puerto}
          onChange={(evento) => setPuerto(evento.target.value)}
        />
        <small>Casi todas las impresoras térmicas usan el 9100.</small>
      </label>
      <fieldset>
        <legend>Ancho del papel</legend>
        {([80, 58] as const).map((ancho) => (
          <label key={ancho}>
            <input
              type="radio"
              name="anchoMm"
              value={ancho}
              checked={anchoMm === ancho}
              onChange={() => setAnchoMm(ancho)}
            />{' '}
            {ancho} mm
          </label>
        ))}
      </fieldset>
      <label>
        Copias de cada factura
        <input
          name="copiasFactura"
          type="number"
          required
          min={1}
          max={5}
          value={copiasFactura}
          onChange={(evento) => setCopiasFactura(evento.target.value)}
        />
      </label>
      <label>
        Copias de cada cierre de caja
        <input
          name="copiasCierre"
          type="number"
          required
          min={1}
          max={5}
          value={copiasCierre}
          onChange={(evento) => setCopiasCierre(evento.target.value)}
        />
      </label>
      {guardado.error && (
        <p className="error" role="alert">
          {guardado.error.message}
        </p>
      )}
      <button type="submit" disabled={guardado.isPending}>
        {guardado.isPending ? 'Guardando…' : impresora ? 'Guardar cambios' : 'Agregar impresora'}
      </button>
      {impresora && (
        <button type="button" onClick={onCancelar}>
          Cancelar
        </button>
      )}
    </form>
  )
}
