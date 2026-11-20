package gt.supermercado.pos.facturacion.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-08.09: el plazo de 15 días para certificar un comprobante en contingencia, con alerta desde el día 12. */
class PlazoContingenciaTest {

    private static final OffsetDateTime EMITIDA = OffsetDateTime.parse("2026-11-01T10:00:00-06:00");

    @Test
    void losDiasSeCuentanDesdeLaEmision() {
        assertThat(PlazoContingencia.dias(EMITIDA, LocalDate.of(2026, 11, 1))).isZero();
        assertThat(PlazoContingencia.dias(EMITIDA, LocalDate.of(2026, 11, 12))).isEqualTo(11);
        assertThat(PlazoContingencia.vence(EMITIDA)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void laAlertaEmpiezaElDia12() {   // crit. 2
        assertThat(PlazoContingencia.enAlerta(EMITIDA, LocalDate.of(2026, 11, 12))).isFalse();
        assertThat(PlazoContingencia.enAlerta(EMITIDA, LocalDate.of(2026, 11, 13))).isTrue();
    }

    @Test
    void elDiaDeEmisionEsElDeGuatemala() {
        // 2 de noviembre a las 03:00 UTC es todavía 1 de noviembre en Guatemala.
        assertThat(PlazoContingencia.dias(OffsetDateTime.parse("2026-11-02T03:00:00Z"), LocalDate.of(2026, 11, 1)))
                .isZero();
    }
}
