package gt.supermercado.pos.facturacion.aplicacion;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-08.10 crit. 6: una factura solo se anula dentro del mes en que se emitió, en la hora de Guatemala. */
class PlazoDeAnulacionTest {

    @Test
    void dentroDelMesDeEmisionSePuedeAnular() {
        assertThat(AnulacionFacturas.dentroDelPlazo(OffsetDateTime.parse("2026-11-02T10:00:00-06:00"),
                YearMonth.of(2026, 11))).isTrue();
    }

    @Test
    void elMesSiguienteYaNo() {
        assertThat(AnulacionFacturas.dentroDelPlazo(OffsetDateTime.parse("2026-10-31T10:00:00-06:00"),
                YearMonth.of(2026, 11))).isFalse();
    }

    @Test
    void elMesSeCuentaEnLaHoraDeGuatemala() {
        // 1 de noviembre a las 03:00 UTC es todavía 31 de octubre en Guatemala.
        assertThat(AnulacionFacturas.dentroDelPlazo(OffsetDateTime.parse("2026-11-01T03:00:00Z"),
                YearMonth.of(2026, 10))).isTrue();
        assertThat(AnulacionFacturas.dentroDelPlazo(OffsetDateTime.parse("2026-11-01T03:00:00Z"),
                YearMonth.of(2026, 11))).isFalse();
    }
}
