package gt.supermercado.pos.reportes.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** HU-10.02 crit. 2: cómo se agrupan las filas de un reporte por período. La semana empieza el lunes. */
public enum Agrupacion {

    DIA("Día"),
    SEMANA("Semana"),
    MES("Mes");

    private final String etiqueta;

    Agrupacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** El primer día del período al que pertenece la fecha. */
    public LocalDate inicioDe(LocalDate fecha) {
        return switch (this) {
            case DIA -> fecha;
            case SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> fecha.withDayOfMonth(1);
        };
    }

    /** El primer día del período siguiente. */
    public LocalDate siguiente(LocalDate inicio) {
        return switch (this) {
            case DIA -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
    }
}
