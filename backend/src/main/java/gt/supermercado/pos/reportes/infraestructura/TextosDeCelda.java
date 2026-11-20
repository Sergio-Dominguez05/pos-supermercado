package gt.supermercado.pos.reportes.infraestructura;

import gt.supermercado.pos.reportes.dominio.TablaReporte.Tipo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Cómo se escribe cada valor en el PDF: igual que en la pantalla (montos con "Q" y dos decimales, hora de Guatemala). */
final class TextosDeCelda {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TextosDeCelda() {
    }

    static String texto(Object valor, Tipo tipo) {
        if (valor == null) {
            return "";
        }
        return switch (tipo) {
            case DINERO -> dinero((BigDecimal) valor);
            case PORCENTAJE -> ((BigDecimal) valor).setScale(1, RoundingMode.HALF_UP).toPlainString() + " %";
            case FECHA -> valor instanceof LocalDate fecha ? FECHA.format(fecha) : valor.toString();
            case FECHA_HORA -> valor instanceof OffsetDateTime momento
                    ? FECHA_HORA.format(momento.atZoneSameInstant(GUATEMALA)) : valor.toString();
            default -> valor.toString();
        };
    }

    static String dinero(BigDecimal monto) {
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        return (redondeado.signum() < 0 ? "-Q" : "Q") + redondeado.abs().toPlainString();
    }

    /** Si la columna se alinea a la derecha, como los números en la pantalla. */
    static boolean esNumero(Tipo tipo) {
        return tipo == Tipo.DINERO || tipo == Tipo.ENTERO || tipo == Tipo.PORCENTAJE;
    }
}
