package gt.supermercado.pos.reportes.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * HU-10.01 crit. 3: los rangos rápidos de fechas. Se calculan con la fecha del servidor en hora de Guatemala, no con
 * la del equipo que pide el reporte. La semana empieza el lunes.
 */
public enum RangoRapido {

    HOY("Hoy"),
    AYER("Ayer"),
    ESTA_SEMANA("Esta semana"),
    ESTE_MES("Este mes"),
    MES_ANTERIOR("Mes anterior");

    private final String etiqueta;

    RangoRapido(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** El rango, con las dos fechas incluidas. Los rangos en curso terminan hoy. */
    public RangoDeFechas para(LocalDate hoy) {
        return switch (this) {
            case HOY -> new RangoDeFechas(hoy, hoy);
            case AYER -> new RangoDeFechas(hoy.minusDays(1), hoy.minusDays(1));
            case ESTA_SEMANA -> new RangoDeFechas(hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), hoy);
            case ESTE_MES -> new RangoDeFechas(hoy.withDayOfMonth(1), hoy);
            case MES_ANTERIOR -> {
                LocalDate inicio = hoy.minusMonths(1).withDayOfMonth(1);
                yield new RangoDeFechas(inicio, inicio.with(TemporalAdjusters.lastDayOfMonth()));
            }
        };
    }

    /** Un rango de fechas, ambas incluidas. */
    public record RangoDeFechas(LocalDate desde, LocalDate hasta) {}
}
