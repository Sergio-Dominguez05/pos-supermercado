package gt.supermercado.pos.reportes.dominio;

import gt.supermercado.pos.compartido.dominio.NegocioException;

import java.time.LocalDate;

/**
 * HU-10.01 Los filtros comunes de los reportes.
 * <ul>
 *   <li>Crit. 1: el rango de fechas es obligatorio en todos los reportes; las dos fechas se incluyen y son días en
 *       hora de Guatemala.</li>
 *   <li>Crit. 2: caja, cajero y tipo de tarjeta son opcionales ({@code null} = todos); cada reporte usa los que le
 *       aplican.</li>
 * </ul>
 */
public record FiltrosReporte(LocalDate desde, LocalDate hasta, Long cajaId, Long cajeroId,
                             TipoTarjetaReporte tipoTarjeta) {

    /** Un reporte no abarca más de cinco años, para que una fecha mal escrita no recorra toda la base. */
    public static final int ANIOS_MAXIMOS = 5;

    public FiltrosReporte {
        if (desde == null || hasta == null) {
            throw new NegocioException("FILTRO_FECHAS_OBLIGATORIAS", "Indique la fecha inicial y la final del reporte.");
        }
        if (hasta.isBefore(desde)) {
            throw new NegocioException("FILTRO_FECHAS_INVERTIDAS",
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (!hasta.isBefore(desde.plusYears(ANIOS_MAXIMOS))) {
            throw new NegocioException("FILTRO_RANGO_EXCESIVO", "El reporte abarca como máximo cinco años.");
        }
    }

    public static FiltrosReporte delPeriodo(LocalDate desde, LocalDate hasta) {
        return new FiltrosReporte(desde, hasta, null, null, null);
    }
}
