import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { CLAVE_FILTROS_REPORTE, obtenerOpcionesDeFiltro, type OpcionesDeFiltro } from '../../api/reportes'
import { useSesion } from '../login/sesion'
import { PERMISO_REPORTES, REPORTES, type CodigoReporte } from './filtros'
import { ReporteDeAnuladas } from './Anuladas'
import { ReporteDeCierres } from './Cierres'
import { ReporteIva } from './Iva'
import { ReporteDeProductos } from './ProductosMasVendidos'
import { ReporteDeTarjetas } from './Tarjetas'
import { ReporteDeCajas } from './VentasPorCaja'
import { ReporteDeHoras } from './VentasPorHora'
import { ReporteDeVentas } from './VentasPeriodo'
import { ReporteDeCajeros } from './VentasPorCajero'

/** EP-10: los reportes, cada uno con los filtros comunes (HU-10.01), para quien tiene el permiso (HU-10.15). */
export function PaginaReportes() {
  const { data: usuario } = useSesion()
  if (!usuario) return null

  return (
    <main className="pagina pagina-ancha">
      <h1>Reportes</h1>
      <p className="subtitulo">
        <Link to="/inicio">Inicio</Link> · Reportes
      </p>
      {usuario.permisos.includes(PERMISO_REPORTES) ? (
        <CargaReportes />
      ) : (
        <p className="tarjeta error">No tiene permiso para consultar reportes.</p>
      )}
    </main>
  )
}

function CargaReportes() {
  const { data, error, isPending } = useQuery({
    queryKey: CLAVE_FILTROS_REPORTE,
    queryFn: obtenerOpcionesDeFiltro,
  })
  if (isPending) return <p className="tarjeta">Cargando filtros…</p>
  if (error) return <p className="tarjeta error">{error.message}</p>
  return <SelectorDeReportes opciones={data} />
}

export function SelectorDeReportes({
  opciones,
  inicial = 'VENTAS_PERIODO',
}: {
  opciones: OpcionesDeFiltro
  inicial?: CodigoReporte
}) {
  const [elegido, setElegido] = useState<CodigoReporte>(inicial)
  return (
    <>
      <div className="pestanas pestanas-reportes" role="tablist" aria-label="Reportes">
        {REPORTES.map((reporte) => (
          <button
            key={reporte.codigo}
            type="button"
            role="tab"
            aria-selected={reporte.codigo === elegido}
            onClick={() => setElegido(reporte.codigo)}
          >
            {reporte.titulo}
          </button>
        ))}
      </div>
      {/* La key reinicia los filtros y el resultado al cambiar de reporte. */}
      {elegido === 'VENTAS_PERIODO' && <ReporteDeVentas key={elegido} opciones={opciones} />}
      {elegido === 'VENTAS_CAJERO' && <ReporteDeCajeros key={elegido} opciones={opciones} />}
      {elegido === 'VENTAS_CAJA' && <ReporteDeCajas key={elegido} opciones={opciones} />}
      {elegido === 'PRODUCTOS' && <ReporteDeProductos key={elegido} opciones={opciones} />}
      {elegido === 'VENTAS_HORA' && <ReporteDeHoras key={elegido} opciones={opciones} />}
      {elegido === 'TARJETAS' && <ReporteDeTarjetas key={elegido} opciones={opciones} />}
      {elegido === 'IVA' && <ReporteIva key={elegido} opciones={opciones} />}
      {elegido === 'CIERRES' && <ReporteDeCierres key={elegido} opciones={opciones} />}
      {elegido === 'ANULADAS' && <ReporteDeAnuladas key={elegido} opciones={opciones} />}
    </>
  )
}
