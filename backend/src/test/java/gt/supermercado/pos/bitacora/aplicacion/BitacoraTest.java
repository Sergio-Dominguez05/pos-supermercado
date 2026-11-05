package gt.supermercado.pos.bitacora.aplicacion;

import gt.supermercado.pos.bitacora.dominio.AccionSensible;
import gt.supermercado.pos.bitacora.dominio.EventoBitacora;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11.02 crit. 1: el servicio completa cada evento con la caja donde ocurre. */
class BitacoraTest {

    private final List<EventoBitacora> registrados = new ArrayList<>();

    @Test
    void completaLaCajaConElContextoDelEvento() {
        Bitacora bitacora = new Bitacora(registrados::add, () -> 7L);

        bitacora.registrar(EventoBitacora.de(1L, "CAJERO", AccionSensible.INICIO_SESION, true, "Inicio"));
        bitacora.registrarIntentoFallido(EventoBitacora.de(1L, "CAJERO", AccionSensible.INICIO_SESION, false, "Fallo"));

        assertThat(registrados).extracting(EventoBitacora::cajaId).containsExactly(7L, 7L);
    }

    @Test
    void respetaLaCajaQueYaTraeElEvento() {
        Bitacora bitacora = new Bitacora(registrados::add, () -> 7L);

        bitacora.registrar(EventoBitacora.de(1L, "ADMIN", AccionSensible.CAJA_CREADA, true, "X").enCaja(3L));

        assertThat(registrados).singleElement().extracting(EventoBitacora::cajaId).isEqualTo(3L);
    }

    @Test
    void fueraDeUnaCajaLaDejaVacia() {
        new Bitacora(registrados::add).registrar(EventoBitacora.de(null, null, AccionSensible.USUARIO_CREADO, true, "Arranque"));

        assertThat(registrados).singleElement().extracting(EventoBitacora::cajaId).isNull();
    }
}
