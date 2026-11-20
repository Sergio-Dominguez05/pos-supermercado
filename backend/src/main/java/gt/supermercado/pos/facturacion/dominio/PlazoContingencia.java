package gt.supermercado.pos.facturacion.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * HU-08.09: el plazo para certificar un comprobante emitido en contingencia (Architecture.md §1.2): 15 días desde su
 * emisión, con una alerta destacada a partir del día 12. Los días se cuentan en la hora de Guatemala.
 */
public final class PlazoContingencia {

    public static final int DIAS_MAXIMOS = 15;
    public static final int DIA_DE_ALERTA = 12;
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private PlazoContingencia() {
    }

    /** Días completos desde la emisión: 0 el mismo día. */
    public static long dias(OffsetDateTime emitidaEn, LocalDate hoy) {
        return ChronoUnit.DAYS.between(emitidaEn.atZoneSameInstant(GUATEMALA).toLocalDate(), hoy);
    }

    /** Crit. 2: el comprobante se acerca al plazo máximo. */
    public static boolean enAlerta(OffsetDateTime emitidaEn, LocalDate hoy) {
        return dias(emitidaEn, hoy) >= DIA_DE_ALERTA;
    }

    /** El último día en que se puede certificar. */
    public static LocalDate vence(OffsetDateTime emitidaEn) {
        return emitidaEn.atZoneSameInstant(GUATEMALA).toLocalDate().plusDays(DIAS_MAXIMOS);
    }

    public static LocalDate hoy() {
        return LocalDate.now(GUATEMALA);
    }
}
