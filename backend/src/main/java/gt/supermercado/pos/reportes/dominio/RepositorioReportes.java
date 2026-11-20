package gt.supermercado.pos.reportes.dominio;

import java.util.List;
import java.util.Optional;

/** Las consultas agregadas de los reportes (EP-10). Solo leen. */
public interface RepositorioReportes {

    /**
     * HU-10.02: las ventas finalizadas por día dentro de los filtros (fecha de finalización en hora de Guatemala,
     * caja y cajero), sin las ventas cuya factura está anulada (crit. 3). Solo los días con ventas.
     */
    List<VentasDelDia> ventasPorDia(FiltrosReporte filtros);

    /** HU-10.03 crit. 1: el resumen de cada cajero con actividad en el período (ver {@link ReporteVentasPorCajero}). */
    List<ReporteVentasPorCajero.Fila> ventasPorCajero(FiltrosReporte filtros);

    /** HU-10.03 crit. 2: las ventas finalizadas y canceladas del cajero de los filtros, en orden cronológico. */
    List<DetalleVentasCajero.Venta> ventasDeCajero(FiltrosReporte filtros);

    /**
     * HU-10.06 crit. 1 y 2: los pagos vigentes de las ventas finalizadas en el período, por tipo de tarjeta y según la
     * factura esté anulada o no. Respeta el filtro de tipo de tarjeta.
     */
    List<ReporteTarjetas.PagosDelTipo> pagosPorTipo(FiltrosReporte filtros);

    /** HU-10.06 crit. 3: las transacciones del tipo de tarjeta de los filtros, en orden cronológico. */
    List<DetallePagosTarjeta.Pago> pagosDeTarjeta(FiltrosReporte filtros);

    /** HU-10.07: las facturas certificadas por día de emisión (hora de Guatemala), solo los días con facturas. */
    List<ReporteIva.Dia> ivaPorDia(FiltrosReporte filtros);

    /** HU-10.07: las facturas del período que aún no están certificadas (pendientes, en contingencia o rechazadas). */
    ReporteIva.SinCertificar sinCertificar(FiltrosReporte filtros);

    /** HU-10.07 crit. 3: las facturas certificadas del período, en orden de emisión. */
    List<DetalleFacturasIva.Factura> facturasIva(FiltrosReporte filtros);

    /** Todas las cajas, también las inactivas: puede haber ventas de una caja que ya no se usa. */
    List<OpcionesDeFiltro.Opcion> cajas();

    /** Los usuarios que pueden registrar ventas o que ya registraron alguna, sin el usuario técnico del sistema. */
    List<OpcionesDeFiltro.Opcion> cajeros();

    Optional<String> nombreCaja(long cajaId);

    Optional<String> nombreCajero(long usuarioId);
}
