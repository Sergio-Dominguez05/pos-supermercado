import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { CLAVE_COBRO, obtenerCobro, registrarPago, type Cobro, type TipoTarjeta } from '../../api/pagos'
import type { Venta } from '../../api/ventas'
import { avisoSonoro, confirmacionSonora } from '../catalogo/sonido'
import { formatoQuetzales } from '../catalogo/textos'
import { descripcionPago, montoValido, soloUltimos4 } from './cobro'

/**
 * HU-06.15 y HU-07.03: el paso de cobro de la venta. Muestra el total a cobrar, que el cajero ingresa en la terminal
 * bancaria (crit. 1), y el formulario para registrar el pago aprobado. Desde aquí se regresa a la venta (HU-06.15
 * crit. 4): el botón "Regresar" o Escape.
 */
export function PasoDeCobro({ venta, onRegresar }: { venta: Venta; onRegresar: () => void }) {
  const { data: cobro } = useQuery({ queryKey: CLAVE_COBRO, queryFn: obtenerCobro })

  return (
    <section
      className="cobro"
      aria-label="Cobro"
      onKeyDown={(evento) => {
        if (evento.key === 'Escape') onRegresar()
      }}
    >
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
      <div className="acciones">
        <button type="button" data-control="regresar-a-la-venta" onClick={onRegresar}>
          Regresar a la venta <kbd>Esc</kbd>
        </button>
      </div>
    </section>
  )
}

/** HU-07.03: el pago ya registrado. La venta queda bloqueada hasta eliminarlo (HU-07.06) o finalizar (HU-07.08). */
export function PagoRegistrado({ cobro }: { cobro: Cobro }) {
  return (
    <div role="status">
      <ul className="lista-pagos">
        {cobro.pagos.map((pago) => (
          <li key={pago.id}>{descripcionPago(pago)}</li>
        ))}
      </ul>
      <p className="pendiente">
        {cobro.pendiente > 0
          ? `Falta cobrar ${formatoQuetzales(cobro.pendiente)}: en esta versión se cobra con una sola tarjeta.`
          : 'El pago cubre el total.'}{' '}
        Finalizar el cobro y emitir la factura estará disponible con HU-07.08 (#63). Para modificar la venta hay que
        eliminar el pago (HU-07.06, #61).
      </p>
    </div>
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
