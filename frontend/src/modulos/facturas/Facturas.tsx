import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { listarReimprimibles, reimprimirFactura, type FacturaEmitida } from '../../api/facturas'
import { formatoQuetzales } from '../catalogo/textos'
import { nitConGuion } from '../clientes/textos'
import { useSesion } from '../login/sesion'

const fecha = (iso: string) => new Date(iso).toLocaleString('es-GT', { dateStyle: 'short', timeStyle: 'short' })

/**
 * HU-08.12 crit. 2: buscar y reimprimir facturas anteriores, según los permisos: el cajero ve las de su turno; el
 * Supervisor y el Administrador, todas. La copia sale en la impresora de esta caja, con la leyenda de reimpresión.
 */
export function PaginaFacturas() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Facturas</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Facturas · <kbd>F9</kbd> en la venta reimprime la última
      </p>
      {usuario.permisos.includes('FACTURA_REIMPRIMIR') ? (
        <BusquedaReimprimibles />
      ) : (
        <p className="tarjeta error">No tiene permiso para reimprimir facturas.</p>
      )}
    </main>
  )
}

function BusquedaReimprimibles() {
  const [texto, setTexto] = useState('')
  const [dia, setDia] = useState('')
  const [consulta, setConsulta] = useState({ texto: '', dia: '' })
  const { data, error, isPending } = useQuery({
    queryKey: ['facturas', 'reimprimibles', consulta.texto, consulta.dia],
    queryFn: () => listarReimprimibles(consulta.texto, consulta.dia),
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
      {data && <ListaReimprimibles facturas={data} />}
    </>
  )
}

export function ListaReimprimibles({ facturas }: { facturas: FacturaEmitida[] }) {
  if (facturas.length === 0) return <p className="tarjeta">No se encontraron facturas.</p>
  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>Factura</th>
            <th>Emitida</th>
            <th>Venta y caja</th>
            <th>Cliente</th>
            <th>Total</th>
            <th>
              <span className="solo-lector">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {facturas.map((factura) => (
            <FilaReimprimible key={factura.facturaId} factura={factura} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FilaReimprimible({ factura }: { factura: FacturaEmitida }) {
  const reimpresion = useMutation({ mutationFn: () => reimprimirFactura(factura.facturaId) })

  return (
    <tr>
      <td>
        {factura.serie}-{factura.numero}
      </td>
      <td>{fecha(factura.emitidaEn)}</td>
      <td>
        N.º {factura.ventaNumero} · {factura.cajaNombre}
      </td>
      <td>
        {factura.receptorId === 'CF' ? 'CF' : `NIT ${nitConGuion(factura.receptorId)}`} · {factura.receptorNombre}
      </td>
      <td>{formatoQuetzales(factura.total)}</td>
      <td className="acciones">
        <button
          type="button"
          disabled={reimpresion.isPending}
          onClick={() => reimpresion.mutate()}
          aria-label={`Reimprimir la factura ${factura.serie}-${factura.numero}`}
        >
          {reimpresion.isPending ? 'Imprimiendo…' : 'Reimprimir'}
        </button>
        {reimpresion.data?.mensaje && (
          <p className="exito" role="status">
            {reimpresion.data.mensaje}
          </p>
        )}
        {reimpresion.error && (
          <p className="error" role="alert">
            {reimpresion.error.message}
          </p>
        )}
      </td>
    </tr>
  )
}
