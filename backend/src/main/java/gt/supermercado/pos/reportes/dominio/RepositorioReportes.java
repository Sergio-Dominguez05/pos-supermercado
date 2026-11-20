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

    /** Todas las cajas, también las inactivas: puede haber ventas de una caja que ya no se usa. */
    List<OpcionesDeFiltro.Opcion> cajas();

    /** Los usuarios que pueden registrar ventas o que ya registraron alguna, sin el usuario técnico del sistema. */
    List<OpcionesDeFiltro.Opcion> cajeros();

    Optional<String> nombreCaja(long cajaId);

    Optional<String> nombreCajero(long usuarioId);
}
