import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  buscarEnBitacora,
  claveDetalleEvento,
  exportarBitacora,
  type FormatoBitacora,
  CLAVE_OPCIONES_BITACORA,
  obtenerDetalleEvento,
  obtenerOpcionesBitacora,
  verificarBitacora,
  type DetalleEvento,
  type FiltrosBitacora,
  type OpcionesBitacora,
  type PaginaBitacora,
  type VerificacionBitacora,
} from '../../api/bitacora'
import { useSesion } from '../login/sesion'
import { fechaYHoraConSegundos, filasDeCambios, filtrosDeHoy, rotuloBitacora, rutaDocumento } from './consulta'

const SIN_PERMISO = 'No tiene permiso para consultar la bitácora.'

/**
 * Bitácora: la consulta de registros con filtros (HU-11.06 y 11.07) y la verificación de su integridad (HU-11.05
 * crit. 3). No existe ninguna opción para editar ni borrar registros (HU-11.05 crit. 1).
 */
export function Bitacora() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Bitácora</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Bitácora
      </p>
      {usuario.permisos.includes('BITACORA_CONSULTAR') ? (
        <>
          <ConsultaRegistros esAdministrador={usuario.rol === 'ADMIN'} />
          <VerificarIntegridad />
        </>
      ) : (
        <p className="tarjeta error">{SIN_PERMISO}</p>
      )}
    </main>
  )
}

function ConsultaRegistros({ esAdministrador }: { esAdministrador: boolean }) {
  const opciones = useQuery({ queryKey: CLAVE_OPCIONES_BITACORA, queryFn: obtenerOpcionesBitacora })
  const [filtros, setFiltros] = useState<FiltrosBitacora>(() => filtrosDeHoy())
  const [pagina, setPagina] = useState(0)
  // Cada búsqueda queda en la bitácora (HU-11.06 crit. 4): no se vuelve a pedir sola al regresar a la ventana.
  const registros = useQuery({
    queryKey: ['bitacora', 'registros', filtros, pagina],
    queryFn: () => buscarEnBitacora(filtros, pagina),
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  return (
    <>
      <FiltrosDeBitacora
        iniciales={filtros}
        opciones={opciones.data}
        onBuscar={(elegidos) => {
          setFiltros(elegidos)
          setPagina(0)
        }}
      />
      {registros.isPending && <p className="tarjeta">Buscando registros…</p>}
      {registros.error && (
        <p className="tarjeta error" role="alert">
          {registros.error.message}
        </p>
      )}
      {/* HU-11.09 crit. 3: solo el Administrador ve la exportación (el servidor también lo exige). */}
      {esAdministrador && registros.data && registros.data.total > 0 && (
        <ExportarBitacora filtros={filtros} total={registros.data.total} />
      )}
      {registros.data && <TablaBitacora pagina={registros.data} onPagina={setPagina} />}
    </>
  )
}

/** HU-11.09 crit. 1: exporta a Excel o CSV los registros de la búsqueda actual, con los mismos filtros. */
export function ExportarBitacora({ filtros, total }: { filtros: FiltrosBitacora; total: number }) {
  const descarga = useMutation({ mutationFn: (formato: FormatoBitacora) => exportarBitacora(filtros, formato) })
  return (
    <div className="tarjeta exportar">
      <div className="acciones" role="group" aria-label="Exportar la bitácora">
        <span>
          Exportar {total === 1 ? 'el registro filtrado' : `los ${total} registros filtrados`}:
        </span>
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('XLSX')}>
          {descarga.isPending && descarga.variables === 'XLSX' ? 'Exportando…' : 'Excel'}
        </button>
        <button type="button" disabled={descarga.isPending} onClick={() => descarga.mutate('CSV')}>
          {descarga.isPending && descarga.variables === 'CSV' ? 'Exportando…' : 'CSV'}
        </button>
      </div>
      {descarga.error && (
        <p className="error" role="alert">
          {descarga.error.message}
        </p>
      )}
      {descarga.data && <p role="status">Se descargó {descarga.data}. La exportación quedó en la bitácora.</p>}
    </div>
  )
}

/** HU-11.07 crit. 1 a 3: fechas, usuario, caja, módulo, acción, resultado y número de documento, combinables. */
export function FiltrosDeBitacora({
  iniciales,
  opciones,
  onBuscar,
}: {
  iniciales: FiltrosBitacora
  opciones?: OpcionesBitacora
  onBuscar: (filtros: FiltrosBitacora) => void
}) {
  const [filtros, setFiltros] = useState(iniciales)
  const cambiar = (cambio: Partial<FiltrosBitacora>) => setFiltros((actuales) => ({ ...actuales, ...cambio }))
  return (
    <form
      className="tarjeta"
      aria-label="Filtrar la bitácora"
      onSubmit={(evento) => {
        evento.preventDefault()
        onBuscar(filtros)
      }}
    >
      <div className="barra-filtros">
        <label>
          Desde
          <input
            name="desde"
            type="date"
            value={filtros.desde}
            onChange={(evento) => cambiar({ desde: evento.target.value })}
          />
        </label>
        <label>
          Hasta
          <input
            name="hasta"
            type="date"
            value={filtros.hasta}
            onChange={(evento) => cambiar({ hasta: evento.target.value })}
          />
        </label>
        <label>
          Usuario
          <select
            name="usuarioId"
            value={filtros.usuarioId}
            onChange={(evento) => cambiar({ usuarioId: evento.target.value })}
          >
            <option value="">Todos</option>
            {opciones?.usuarios.map((usuario) => (
              <option key={usuario.id} value={usuario.id}>
                {usuario.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Caja
          <select name="cajaId" value={filtros.cajaId} onChange={(evento) => cambiar({ cajaId: evento.target.value })}>
            <option value="">Todas</option>
            {opciones?.cajas.map((caja) => (
              <option key={caja.id} value={caja.id}>
                {caja.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Módulo
          <select name="modulo" value={filtros.modulo} onChange={(evento) => cambiar({ modulo: evento.target.value })}>
            <option value="">Todos</option>
            {opciones?.modulos.map((modulo) => (
              <option key={modulo} value={modulo}>
                {modulo}
              </option>
            ))}
          </select>
        </label>
        <label>
          Acción
          <select name="accion" value={filtros.accion} onChange={(evento) => cambiar({ accion: evento.target.value })}>
            <option value="">Todas</option>
            {opciones?.acciones.map((accion) => (
              <option key={accion} value={accion}>
                {accion}
              </option>
            ))}
          </select>
        </label>
        <label>
          Resultado
          <select
            name="exitoso"
            value={filtros.exitoso}
            onChange={(evento) => cambiar({ exitoso: evento.target.value as FiltrosBitacora['exitoso'] })}
          >
            <option value="">Todos</option>
            <option value="true">Exitoso</option>
            <option value="false">Fallido</option>
          </select>
        </label>
        <label>
          Documento
          <input
            name="documento"
            maxLength={40}
            placeholder="Venta, factura (A1-77) o cierre"
            value={filtros.documento}
            onChange={(evento) => cambiar({ documento: evento.target.value })}
          />
        </label>
        <button type="submit" className="boton">
          Buscar
        </button>
      </div>
    </form>
  )
}

/** HU-11.06 crit. 1 y 2: fecha y hora, usuario, caja, módulo, acción y resultado, del más reciente, por páginas. */
export function TablaBitacora({
  pagina: datos,
  onPagina,
}: {
  pagina: PaginaBitacora
  onPagina: (pagina: number) => void
}) {
  const { registros, pagina, tamanoPagina, total } = datos
  return (
    <section aria-label="Registros de la bitácora">
      <p className="tarjeta" role="status">
        {rotuloBitacora(pagina, tamanoPagina, total, registros.length)}
      </p>
      {registros.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha y hora</th>
                <th>Usuario</th>
                <th>Caja</th>
                <th>Módulo</th>
                <th>Acción</th>
                <th>Resultado</th>
              </tr>
            </thead>
            <tbody>
              {registros.map((registro) => (
                <tr key={registro.id}>
                  <td>
                    <Link to={`/bitacora/${registro.id}`}>{fechaYHoraConSegundos(registro.ocurridoEn)}</Link>
                  </td>
                  <td>{registro.usuario ?? 'Sistema'}</td>
                  <td>{registro.caja ?? '—'}</td>
                  <td>{registro.modulo}</td>
                  <td>
                    {registro.accion}
                    <small> · {registro.descripcion}</small>
                  </td>
                  <td className={registro.exitoso ? 'exito' : 'error'}>{registro.exitoso ? 'Exitoso' : 'Fallido'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {total > tamanoPagina && (
        <div className="acciones">
          <button type="button" disabled={pagina === 0} onClick={() => onPagina(pagina - 1)}>
            Anteriores
          </button>
          <button type="button" disabled={(pagina + 1) * tamanoPagina >= total} onClick={() => onPagina(pagina + 1)}>
            Siguientes
          </button>
        </div>
      )}
    </section>
  )
}

/** HU-11.08: el detalle completo de un registro de la bitácora. */
export function PaginaEventoBitacora() {
  const { data: usuario } = useSesion()
  const id = Number(useParams().id)
  const puede = Boolean(usuario?.permisos.includes('BITACORA_CONSULTAR'))
  // Abrir el detalle queda en la bitácora: se pide una sola vez.
  const detalle = useQuery({
    queryKey: claveDetalleEvento(id),
    queryFn: () => obtenerDetalleEvento(id),
    enabled: puede && Number.isInteger(id),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
  if (!usuario) return null

  return (
    <main className="pagina">
      <h1>Detalle del registro</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · <Link to="/bitacora">Bitácora</Link> · Detalle
      </p>
      {!puede ? (
        <p className="tarjeta error">{SIN_PERMISO}</p>
      ) : detalle.isPending ? (
        <p className="tarjeta">Cargando el registro…</p>
      ) : detalle.error ? (
        <p className="tarjeta error">{detalle.error.message}</p>
      ) : (
        <VistaEvento detalle={detalle.data} permisos={usuario.permisos} />
      )}
    </main>
  )
}

/** Crit. 1: todo lo guardado, con los valores anteriores y nuevos. Crit. 2: el documento, si se puede abrir. */
export function VistaEvento({ detalle, permisos }: { detalle: DetalleEvento; permisos: readonly string[] }) {
  const { registro, documento } = detalle
  const filas = filasDeCambios(detalle.cambios)
  const ruta = documento && rutaDocumento(documento, permisos)
  return (
    <section className="tarjeta" aria-label="Registro de la bitácora">
      <h2>
        {registro.accion} · {registro.exitoso ? 'Exitoso' : 'Fallido'}
      </h2>
      <p>{registro.descripcion}</p>
      <dl className="resumen">
        <dt>Registro N.º</dt>
        <dd>{registro.secuencia}</dd>
        <dt>Fecha y hora</dt>
        <dd>{fechaYHoraConSegundos(registro.ocurridoEn)}</dd>
        <dt>Usuario</dt>
        <dd>
          {registro.usuario ?? 'Sistema'}
          {registro.rol && <small> · {registro.rol}</small>}
        </dd>
        <dt>Caja</dt>
        <dd>{registro.caja ?? '—'}</dd>
        <dt>Módulo</dt>
        <dd>{registro.modulo}</dd>
        {detalle.entidad && (
          <>
            <dt>Sobre</dt>
            <dd>
              {detalle.entidad}
              {detalle.entidadId !== null && ` N.º ${detalle.entidadId}`}
            </dd>
          </>
        )}
        {documento && (
          <>
            <dt>Documento</dt>
            <dd>{ruta ? <Link to={ruta}>{documento.etiqueta}</Link> : documento.etiqueta}</dd>
          </>
        )}
      </dl>
      {filas.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Dato</th>
                <th>Valor anterior</th>
                <th>Valor nuevo</th>
              </tr>
            </thead>
            <tbody>
              {filas.map((fila) => (
                <tr key={fila.campo}>
                  <td>{fila.campo}</td>
                  {fila.valor !== undefined ? (
                    <td colSpan={2}>{fila.valor}</td>
                  ) : (
                    <>
                      <td>{fila.antes}</td>
                      <td>{fila.despues}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function VerificarIntegridad() {
  const verificacion = useMutation({ mutationFn: verificarBitacora })

  return (
    <section className="tarjeta formulario">
      <h2>Integridad</h2>
      <p>
        Cada registro está encadenado con el anterior. Verificar recalcula toda la cadena y avisa si falta algún
        registro o si alguno fue alterado.
      </p>
      {verificacion.error && (
        <p className="error" role="alert">
          {verificacion.error.message}
        </p>
      )}
      {verificacion.data && <ResultadoVerificacion resultado={verificacion.data} />}
      <button type="button" disabled={verificacion.isPending} onClick={() => verificacion.mutate()}>
        {verificacion.isPending ? 'Verificando…' : 'Verificar integridad'}
      </button>
    </section>
  )
}

export function ResultadoVerificacion({ resultado }: { resultado: VerificacionBitacora }) {
  if (resultado.integra) {
    return (
      <p className="exito" role="status">
        La bitácora está íntegra: {resultado.registros} registros encadenados, sin faltantes ni alteraciones.
      </p>
    )
  }
  return (
    <div className="error" role="alert">
      <p>La bitácora no está íntegra. Problemas encontrados:</p>
      <ul>
        {resultado.problemas.map((problema) => (
          <li key={`${problema.secuencia}-${problema.descripcion}`}>
            Registro {problema.secuencia}: {problema.descripcion}
          </li>
        ))}
      </ul>
    </div>
  )
}
