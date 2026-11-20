import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { anularFactura, buscarFacturas, type FacturaEmitida } from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { nitConGuion } from '../clientes/textos'
import { useSesion } from '../login/sesion'
import { avisoAnulada, confirmacionAnular } from './textos'

const fecha = (iso: string) => new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })

/** HU-08.10 Anular una factura. Solo para el Supervisor y el Administrador (crit. 5). */
export function PaginaAnularFacturas() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Anular facturas</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Anular facturas
      </p>
      {usuario.permisos.includes('FACTURA_ANULAR') ? (
        <BusquedaFacturas />
      ) : (
        <p className="tarjeta error">No tiene permiso para anular facturas.</p>
      )}
    </main>
  )
}

/** Crit. 1: por número, serie, autorización o fecha. Sin criterios, las facturas más recientes. */
function BusquedaFacturas() {
  const [texto, setTexto] = useState('')
  const [dia, setDia] = useState('')
  const [consulta, setConsulta] = useState({ texto: '', dia: '' })
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ['facturas', 'busqueda', consulta.texto, consulta.dia],
    queryFn: () => buscarFacturas(consulta.texto, consulta.dia),
  })

  return (
    <>
      <form
        className="tarjeta en-linea"
        aria-label="Buscar facturas"
        onSubmit={(evento) => {
          evento.preventDefault()
          setConsulta({ texto, dia })
        }}
      >
        <label>
          Número, serie o autorización
          <input name="buscar" maxLength={60} value={texto} onChange={(evento) => setTexto(evento.target.value)} />
        </label>
        <label>
          Fecha de emisión
          <input name="fecha" type="date" value={dia} onChange={(evento) => setDia(evento.target.value)} />
        </label>
        <button type="submit">Buscar</button>
      </form>
      {isPending && <p className="tarjeta">Buscando facturas…</p>}
      {error && <p className="tarjeta error">{error.message}</p>}
      {data && <ResultadosFacturas facturas={data} onAnulada={() => void refetch()} />}
    </>
  )
}

export function ResultadosFacturas({ facturas, onAnulada }: { facturas: FacturaEmitida[]; onAnulada: () => void }) {
  if (facturas.length === 0) return <p className="tarjeta">No se encontraron facturas.</p>
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>Factura</th>
            <th>Autorización</th>
            <th>Emitida</th>
            <th>Venta y caja</th>
            <th>Cliente</th>
            <th>Total</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          {facturas.map((factura) => (
            <FilaFactura key={factura.facturaId} factura={factura} onAnulada={onAnulada} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FilaFactura({ factura, onAnulada }: { factura: FacturaEmitida; onAnulada: () => void }) {
  return (
    <tr>
      <td>
        {/* HU-08.14: el detalle de la factura. */}
        <Link to={`/facturas/${factura.facturaId}`}>
          {factura.serie}-{factura.numero}
        </Link>
      </td>
      <td>
        <small>{factura.autorizacion.toUpperCase()}</small>
      </td>
      <td>{fecha(factura.emitidaEn)}</td>
      <td>
        N.º {factura.ventaNumero} · {factura.cajaNombre}
      </td>
      <td>
        {factura.receptorId === 'CF' ? 'CF' : `NIT ${nitConGuion(factura.receptorId)}`} · {factura.receptorNombre}
      </td>
      <td>{formatoQuetzales(factura.total)}</td>
      <td>
        <EstadoFactura factura={factura} onAnulada={onAnulada} />
      </td>
    </tr>
  )
}

/**
 * Crit. 2 a 4: la anulación pide el motivo y una confirmación. Crit. 6 y D2: con el turno de la venta cerrado ya no se
 * ofrece.
 */
export function EstadoFactura({ factura, onAnulada }: { factura: FacturaEmitida; onAnulada: () => void }) {
  const [abierta, setAbierta] = useState(false)
  const [motivo, setMotivo] = useState('')
  const anulacion = useMutation({
    mutationFn: () => anularFactura(factura.facturaId, motivo),
    onSuccess: () => {
      setAbierta(false)
      onAnulada()
    },
  })

  if (factura.estado === 'ANULADA') {
    return (
      <span>
        <strong>Anulada</strong>
        {factura.motivoAnulacion && <small> · {factura.motivoAnulacion}</small>}
      </span>
    )
  }
  if (!factura.turnoAbierto) return <span>Certificada · turno cerrado, no se puede anular</span>
  return (
    <>
      <span>Certificada </span>
      {anulacion.data && (
        <p className="exito" role="status">
          {avisoAnulada(anulacion.data.serie, anulacion.data.numero)}
        </p>
      )}
      {!abierta ? (
        <button type="button" onClick={() => setAbierta(true)} aria-label={`Anular la factura ${factura.numero}`}>
          Anular
        </button>
      ) : (
        <form
          className="formulario"
          aria-label={`Anular la factura ${factura.numero}`}
          onSubmit={(evento) => {
            evento.preventDefault()
            if (!window.confirm(confirmacionAnular(factura.serie, factura.numero, factura.ventaNumero))) return
            anulacion.mutate()
          }}
        >
          <label>
            Motivo de la anulación
            <textarea
              name="motivo"
              required
              minLength={5}
              maxLength={255}
              value={motivo}
              onChange={(evento) => setMotivo(evento.target.value)}
            />
          </label>
          {anulacion.error && (
            <p className="error" role="alert">
              {anulacion.error.message}
            </p>
          )}
          <div className="acciones">
            <button type="submit" disabled={anulacion.isPending}>
              {anulacion.isPending ? 'Anulando…' : 'Confirmar anulación'}
            </button>
            <button type="button" onClick={() => setAbierta(false)}>
              Cancelar
            </button>
          </div>
        </form>
      )}
    </>
  )
}
