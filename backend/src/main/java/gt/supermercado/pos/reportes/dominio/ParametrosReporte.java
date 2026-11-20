package gt.supermercado.pos.reportes.dominio;

import java.time.LocalDate;

/**
 * Lo que se pide al generar o exportar un reporte: los filtros comunes (HU-10.01) y los propios de algunos reportes.
 * Cada reporte usa los que le aplican; los demás se ignoran.
 *
 * @param agrupacion         HU-10.02; {@code null} = por día
 * @param soloConDiferencias HU-10.08 crit. 3
 * @param anuladaPorId       HU-10.09 crit. 2; {@code null} = cualquiera
 * @param ordenProductos     HU-10.05 crit. 2; {@code null} = por cantidad
 * @param limite             HU-10.05 crit. 3; {@code null} = {@value ReporteProductosMasVendidos#LIMITE_POR_OMISION}
 */
public record ParametrosReporte(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                                TipoTarjetaReporte tipoTarjeta, Agrupacion agrupacion, boolean soloConDiferencias,
                                Long anuladaPorId, OrdenProductos ordenProductos, Integer limite) {

    public static ParametrosReporte comunes(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, null, null, false, null, null, null);
    }

    public ParametrosReporte conTarjeta(TipoTarjetaReporte tipo) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipo, agrupacion, soloConDiferencias, anuladaPorId,
                ordenProductos, limite);
    }

    public ParametrosReporte conAgrupacion(Agrupacion grupo) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipoTarjeta, grupo, soloConDiferencias, anuladaPorId,
                ordenProductos, limite);
    }

    public ParametrosReporte conSoloDiferencias(boolean solo) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipoTarjeta, agrupacion, solo, anuladaPorId,
                ordenProductos, limite);
    }

    public ParametrosReporte conAnuladaPor(Long usuarioId) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipoTarjeta, agrupacion, soloConDiferencias,
                usuarioId, ordenProductos, limite);
    }

    public ParametrosReporte conProductos(OrdenProductos orden, Integer cuantos) {
        return new ParametrosReporte(desde, hasta, cajaId, cajeroId, tipoTarjeta, agrupacion, soloConDiferencias,
                anuladaPorId, orden, cuantos);
    }
}
