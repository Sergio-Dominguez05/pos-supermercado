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

    /**
     * HU-10.04: por caja, las ventas finalizadas en el período sin las de facturas anuladas y los turnos abiertos en
     * el período (los dos por caja y cajero de los filtros). Trae las cajas activas aunque no tengan actividad y las
     * inactivas que sí la tuvieron.
     */
    List<ReporteVentasPorCaja.VentasDeCaja> ventasPorCaja(FiltrosReporte filtros);

    /**
     * HU-10.05: los productos de las líneas vigentes de las ventas finalizadas en el período (por caja y cajero), sin
     * las ventas de facturas anuladas (crit. 4), ordenados por cantidad o monto y recortados a {@code limite}.
     */
    ReporteProductosMasVendidos.Resultado productosMasVendidos(FiltrosReporte filtros, OrdenProductos orden,
                                                              int limite);

    /** HU-10.12 crit. 1: los turnos abiertos ahora, con su caja y su cajero, del más antiguo al más reciente. */
    List<PanelDelDia.CajaAbierta> cajasAbiertas();

    /**
     * HU-10.12 crit. 2: los comprobantes en contingencia (de cualquier día), los códigos no encontrados sin resolver
     * del día y los cierres del día con diferencia; el día es en hora de Guatemala.
     */
    PanelDelDia.Alertas alertas(java.time.LocalDate dia);

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

    /**
     * HU-10.08: los turnos cerrados en el período (hora de Guatemala), por caja y cajero del turno, en orden de
     * cierre; con {@code soloConDiferencias}, solo los que tienen diferencia.
     */
    List<ReporteCierres.Cierre> cierres(FiltrosReporte filtros, boolean soloConDiferencias);

    /**
     * HU-10.09: las facturas anuladas en el período (fecha de anulación), por caja y cajero de la venta y por quien
     * anuló ({@code null} = cualquiera), en orden de anulación.
     */
    List<ReporteAnuladas.Anulada> anuladas(FiltrosReporte filtros, Long anuladaPorId);

    /** Los usuarios que pueden anular facturas o que ya anularon alguna (HU-10.09 crit. 2). */
    List<OpcionesDeFiltro.Opcion> anuladores();

    /** HU-10.13 crit. 2: el emisor y el establecimiento de la sucursal principal, si ya se configuraron. */
    DatosEmpresa empresa();

    /** Todas las cajas, también las inactivas: puede haber ventas de una caja que ya no se usa. */
    List<OpcionesDeFiltro.Opcion> cajas();

    /** Los usuarios que pueden registrar ventas o que ya registraron alguna, sin el usuario técnico del sistema. */
    List<OpcionesDeFiltro.Opcion> cajeros();

    Optional<String> nombreCaja(long cajaId);

    Optional<String> nombreCajero(long usuarioId);
}
