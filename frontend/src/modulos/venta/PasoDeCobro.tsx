import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import {
  CLAVE_COBRO,
  eliminarPago,
  finalizarCobro,
  obtenerCobro,
  registrarPago,
  type Cobro,
  type Pago,
  type TipoTarjeta,
  type VentaFinalizada,
} from '../../api/pagos'
import { CLAVE_VENTA_EN_CURSO, type Venta } from '../../api/ventas'
import { avisoSonoro, confirmacionSonora } from '../catalogo/sonido'
import { formatoQuetzales } from '../catalogo/textos'
import { confirmacionEliminarPago, descripcionPago, montoValido, motivoSinFinalizar, soloUltimos4 } from './cobro'

/**
 * HU-06.15 y HU-07.03: el paso de cobro de la venta. Muestra el total a cobrar, que el cajero ingresa en la terminal
 * bancaria (crit. 1), y el formulario para registrar el pago aprobado. Desde aquí se regresa a la venta (HU-06.15
 * crit. 4): el botón "Regresar" o Escape.
 */
export function PasoDeCobro({
  venta,
  onRegresar,
  onFinalizada,
}: {
  venta: Venta
  onRegresar: () => void
  onFinalizada?: (finalizada: VentaFinalizada) => void
}) {
  const { data: cobro } = useQuery({ queryKey: CLAVE_COBRO, queryFn: obtenerCobro })

  // Escape regresa a la venta esté donde esté el foco: al registrar o eliminar un pago, el formulario se reemplaza y
  // el foco queda fuera del cobro.
  useEffect(() => {
    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') onRegresar()
    }
    window.addEventListener('keydown', alPresionar)
    return () => window.removeEventListener('keydown', alPresionar)
  }, [onRegresar])

  return (
    <section className="cobro" aria-label="Cobro">
      <h3>Cobro con tarjeta</h3>
      <dl className="totales" aria-label="Total a cobrar">
        <dt>Artículos</dt>
        <dd>{venta.articulos}</dd>
        <dt>Total a cobrar</dt>
        <dd className="total">{formatoQuetzales(cobro?.total ?? venta.total)}</dd>
        {cobro && cobro.pagado > 0 && (
          <>
            <dt>Pagado</dt>
            <dd>{formatoQuetzales(cobro.pagado)}</dd>
            <dt>Pendiente</dt>
            <dd>{formatoQuetzales(cobro.pendiente)}</dd>
          </>
        )}
      </dl>
      {cobro && cobro.pagos.length > 0 ? <PagoRegistrado cobro={cobro} /> : cobro && <FormularioPago cobro={cobro} />}
      {cobro && <FinalizarCobro cobro={cobro} onFinalizada={onFinalizada} />}
      <div className="acciones">
        <button type="button" data-control="regresar-a-la-venta" onClick={onRegresar}>
          Regresar a la venta <kbd>Esc</kbd>
        </button>
      </div>
    </section>
  )
}

/**
 * HU-07.03: el pago ya registrado. La venta queda bloqueada hasta eliminarlo o finalizar el cobro (HU-07.08). HU-07.06:
 * cada pago se puede eliminar mientras la venta no se haya finalizado (crit. 1).
 */
export function PagoRegistrado({ cobro }: { cobro: Cobro }) {
  return (
    <div role="status">
      <ul className="lista-pagos">
        {cobro.pagos.map((pago) => (
          <li key={pago.id}>
            <span>{descripcionPago(pago)}</span>
            <EliminarPago pago={pago} />
          </li>
        ))}
      </ul>
      <p className="pendiente">
        {cobro.pendiente > 0
          ? `Falta cobrar ${formatoQuetzales(cobro.pendiente)}: en esta versión se cobra con una sola tarjeta.`
          : 'El pago cubre el total.'}{' '}
        Para modificar la venta, elimine el pago.
      </p>
    </div>
  )
}

/**
 * HU-07.07: la opción de finalizar solo se habilita cuando los pagos cubren el total (crit. 2); mientras falte dinero
 * queda deshabilitada con el motivo (crit. 1), y el servidor también lo exige (`PAGO_INCOMPLETO`).
 *
 * HU-07.08: al confirmar, el servidor registra la venta con sus pagos y emite la factura (crit. 1); la pantalla pasa a
 * la siguiente venta (crit. 5). Si algo falla, la venta sigue en curso con sus pagos: se muestra el motivo y se puede
 * reintentar (crit. 4). Con el pago completo, el botón recibe el foco para confirmar con Enter.
 */
export function FinalizarCobro({
  cobro,
  onFinalizada,
}: {
  cobro: Cobro
  onFinalizada?: (finalizada: VentaFinalizada) => void
}) {
  const clienteConsultas = useQueryClient()
  const motivo = motivoSinFinalizar(cobro)
  // El botón ya está en pantalla (deshabilitado) antes de registrar el pago: recibe el foco cuando se habilita.
  const boton = useRef<HTMLButtonElement>(null)
  const completo = motivo === undefined
  useEffect(() => {
    if (completo) boton.current?.focus()
  }, [completo])
  const finalizacion = useMutation({
    mutationFn: () => finalizarCobro(),
    onSuccess: (finalizada) => {
      confirmacionSonora()
      clienteConsultas.removeQueries({ queryKey: CLAVE_COBRO })
      onFinalizada?.(finalizada)
      return clienteConsultas.invalidateQueries({ queryKey: CLAVE_VENTA_EN_CURSO })
    },
    onError: () => {
      avisoSonoro()
      // Por ejemplo, PAGO_INCOMPLETO si el pago cambió en otra pestaña: se muestra el cobro actualizado.
      return clienteConsultas.invalidateQueries({ queryKey: CLAVE_COBRO })
    },
  })

  return (
    <div className="finalizar-cobro">
      <button
        type="button"
        data-control="finalizar-cobro"
        className="principal"
        ref={boton}
        autoFocus={completo}
        disabled={!completo || finalizacion.isPending}
        aria-describedby="motivo-sin-finalizar"
        onClick={() => finalizacion.mutate()}
      >
        {finalizacion.isPending ? 'Finalizando…' : 'Finalizar cobro'}
      </button>
      {motivo && (
        <p id="motivo-sin-finalizar" className="pendiente">
          {motivo}
        </p>
      )}
      {finalizacion.error && (
        <p className="error" role="alert">
          {finalizacion.error.message} La venta sigue en curso con sus pagos: puede reintentar.
        </p>
      )}
    </div>
  )
}

/**
 * HU-07.06: quita un pago registrado por error, después de confirmarlo. El servidor lo registra en la bitácora
 * (crit. 4) y devuelve el pendiente recalculado, que la pantalla muestra al instante (crit. 2): vuelve el formulario
 * para registrar el pago correcto.
 */
export function EliminarPago({ pago }: { pago: Pago }) {
  const clienteConsultas = useQueryClient()
  const eliminacion = useMutation({
    mutationFn: () => eliminarPago(pago.id),
    onSuccess: (actualizado) => clienteConsultas.setQueryData(CLAVE_COBRO, actualizado),
    onError: () => avisoSonoro(),
  })

  return (
    <>
      <button
        type="button"
        data-control="eliminar-pago"
        disabled={eliminacion.isPending}
        onClick={() => {
          if (window.confirm(confirmacionEliminarPago(pago))) eliminacion.mutate()
        }}
      >
        {eliminacion.isPending ? 'Eliminando…' : 'Eliminar pago'}
      </button>
      {eliminacion.error && (
        <span className="error" role="alert">
          {eliminacion.error.message}
        </span>
      )}
    </>
  )
}

/**
 * HU-07.03: el cajero procesa la tarjeta en la terminal bancaria y registra aquí el pago aprobado.
 * - Crit. 2: el monto se propone igual al total pendiente.
 * - Crit. 3: tipo de tarjeta y número de autorización obligatorios.
 * - Crit. 4: los últimos 4 dígitos son opcionales; el campo no admite más de 4 dígitos.
 * - Crit. 5: el servidor rechaza un monto mayor al pendiente.
 * - Crit. 6: si la terminal rechaza la tarjeta, no se registra nada: se intenta con otra o se cancela la venta.
 */
export function FormularioPago({ cobro }: { cobro: Cobro }) {
  const clienteConsultas = useQueryClient()
  const [tipo, setTipo] = useState<TipoTarjeta>()
  const [autorizacion, setAutorizacion] = useState('')
  const [ultimos4, setUltimos4] = useState('')
  const [monto, setMonto] = useState(cobro.pendiente.toFixed(2))
  const [aviso, setAviso] = useState<string>()
  const registro = useMutation({
    mutationFn: () =>
      registrarPago({ tipoTarjeta: tipo, autorizacion, ultimos4: ultimos4 || undefined, monto: Number(monto) }),
    onSuccess: (actualizado) => {
      clienteConsultas.setQueryData(CLAVE_COBRO, actualizado)
      confirmacionSonora()
    },
    onError: () => avisoSonoro(),
  })

  return (
    <form
      className="formulario"
      aria-label="Pago con tarjeta"
      onSubmit={(evento) => {
        evento.preventDefault()
        const problema = !tipo
          ? 'Indique si la tarjeta es de débito o de crédito.'
          : !autorizacion.trim()
            ? 'Ingrese el número de autorización que entregó la terminal bancaria.'
            : !montoValido(monto)
              ? 'El monto debe ser mayor que cero, en quetzales y centavos.'
              : undefined
        setAviso(problema)
        if (problema) avisoSonoro()
        else registro.mutate()
      }}
    >
      <p>
        Ingrese {formatoQuetzales(cobro.pendiente)} en la terminal bancaria y registre el pago cuando la terminal lo
        apruebe. Si la rechaza, no registre nada: intente con otra tarjeta o regrese y cancele la venta.
      </p>
      <fieldset>
        <legend>Tipo de tarjeta</legend>
        <label>
          <input
            type="radio"
            name="tipoTarjeta"
            value="DEBITO"
            autoFocus
            checked={tipo === 'DEBITO'}
            onChange={() => setTipo('DEBITO')}
          />{' '}
          Débito
        </label>
        <label>
          <input
            type="radio"
            name="tipoTarjeta"
            value="CREDITO"
            checked={tipo === 'CREDITO'}
            onChange={() => setTipo('CREDITO')}
          />{' '}
          Crédito
        </label>
      </fieldset>
      <label>
        Número de autorización
        <input
          name="autorizacion"
          autoComplete="off"
          maxLength={30}
          value={autorizacion}
          onChange={(evento) => setAutorizacion(evento.target.value)}
        />
      </label>
      <label>
        Últimos 4 dígitos (opcional)
        <input
          name="ultimos4"
          autoComplete="off"
          inputMode="numeric"
          maxLength={4}
          value={ultimos4}
          onChange={(evento) => setUltimos4(soloUltimos4(evento.target.value))}
        />
      </label>
      <label>
        Monto (Q)
        <input
          name="monto"
          autoComplete="off"
          inputMode="decimal"
          value={monto}
          onChange={(evento) => setMonto(evento.target.value)}
        />
      </label>
      {(aviso || registro.error) && (
        <p className="error" role="alert">
          {aviso ?? registro.error?.message}
        </p>
      )}
      <button type="submit" disabled={registro.isPending}>
        {registro.isPending ? 'Registrando…' : 'Registrar pago'}
      </button>
    </form>
  )
}
