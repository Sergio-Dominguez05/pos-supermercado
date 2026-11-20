import type { ResumenDeTurno } from '../../api/turnos'
import { formatoQuetzales } from '../catalogo/textos'

/** Totales por tarjeta y anulaciones compartidos por HU-09.06 y el cierre de caja. */
export function ResumenCobrado({ resumen }: { resumen: ResumenDeTurno }) {
  return (
    <dl className="resumen">
      <dt>Ventas</dt>
      <dd>{resumen.ventas}</dd>
      <dt>Tarjeta de débito</dt>
      <dd>{formatoQuetzales(resumen.debito)}</dd>
      <dt>Tarjeta de crédito</dt>
      <dd>{formatoQuetzales(resumen.credito)}</dd>
      <dt>Facturas anuladas ({resumen.anuladas})</dt>
      <dd>{formatoQuetzales(resumen.totalAnulado)}</dd>
      <dt><strong>Total registrado</strong></dt>
      <dd><strong>{formatoQuetzales(resumen.totalRegistrado)}</strong></dd>
      {resumen.contingencias > 0 && (
        <>
          <dt>En contingencia</dt>
          <dd>{resumen.contingencias} por certificar</dd>
        </>
      )}
    </dl>
  )
}
