import type { PanelDelDia } from '../../api/reportes'

/** Una alerta del panel: qué pasa y dónde se atiende. */
export interface AlertaDelPanel {
  texto: string
  ruta: string
}

const plural = (cantidad: number, uno: string, varios: string) => (cantidad === 1 ? uno : varios)

/** HU-10.12 crit. 2: solo las alertas que tienen algo pendiente, cada una con la pantalla donde se atiende. */
export const alertasDelPanel = ({
  contingenciaPendientes,
  codigosNoEncontrados,
  cierresConDiferencia,
}: PanelDelDia['alertas']): AlertaDelPanel[] => {
  const alertas: AlertaDelPanel[] = []
  if (contingenciaPendientes > 0)
    alertas.push({
      texto: `${contingenciaPendientes} ${plural(contingenciaPendientes, 'comprobante en contingencia pendiente', 'comprobantes en contingencia pendientes')} de certificar.`,
      ruta: '/facturas/contingencia',
    })
  if (codigosNoEncontrados > 0)
    alertas.push({
      texto: `${codigosNoEncontrados} ${plural(codigosNoEncontrados, 'código no encontrado', 'códigos no encontrados')} en caja hoy.`,
      ruta: '/catalogo',
    })
  if (cierresConDiferencia > 0)
    alertas.push({
      texto: `${cierresConDiferencia} ${plural(cierresConDiferencia, 'cierre de caja', 'cierres de caja')} de hoy con diferencia.`,
      ruta: '/caja/cierres',
    })
  return alertas
}
