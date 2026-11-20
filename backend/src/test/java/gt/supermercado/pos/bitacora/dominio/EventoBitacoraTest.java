package gt.supermercado.pos.bitacora.dominio;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11.03: valor anterior y nuevo de cada campo, y los datos sensibles solo como "modificado". */
class EventoBitacoraTest {

    @Test
    void losCamposComunesGuardanSuValorAnteriorYElNuevo() {   // crit. 1
        EventoBitacora evento = evento(Map.of("nombre", EventoBitacora.cambio("Ana", "Ana María")));

        assertThat(evento.cambios()).containsEntry("nombre", Map.of("antes", "Ana", "despues", "Ana María"));
    }

    @Test
    void unDatoSensibleSeGuardaComoModificadoAunqueSeEnvieSuValor() {   // crit. 2
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("usuario", EventoBitacora.cambio("fel-1", "fel-2"));
        cambios.put("secreto", EventoBitacora.cambio("Llave-Vieja", "Llave-Nueva"));
        cambios.put("contrasenaSmtp", "Clave-SMTP-1");
        cambios.put("PIN", "4821");

        EventoBitacora evento = evento(cambios);

        assertThat(evento.cambios()).containsEntry("usuario", Map.of("antes", "fel-1", "despues", "fel-2"))
                .containsEntry("secreto", EventoBitacora.MODIFICADO)
                .containsEntry("contrasenaSmtp", EventoBitacora.MODIFICADO)
                .containsEntry("PIN", EventoBitacora.MODIFICADO);
        assertThat(evento.cambios().toString()).doesNotContain("Llave", "Clave-SMTP", "4821");
        assertThat(evento.cambios().keySet()).containsExactly("usuario", "secreto", "contrasenaSmtp", "PIN");
    }

    @Test
    void reconoceLosNombresSensiblesSinConfundirOtrosCampos() {
        assertThat(EventoBitacora.esSensible("secreto")).isTrue();
        assertThat(EventoBitacora.esSensible("credencialTemporal")).isTrue();
        assertThat(EventoBitacora.esSensible("Contraseña")).isTrue();
        assertThat(EventoBitacora.esSensible("tokenEquipo")).isTrue();
        // El permiso CREDENCIAL_RESTABLECER es un código, no un dato sensible; tampoco "total" ni "pinta".
        assertThat(EventoBitacora.esSensible("CREDENCIAL_RESTABLECER")).isFalse();
        assertThat(EventoBitacora.esSensible("total")).isFalse();
        assertThat(EventoBitacora.esSensible("pinta")).isFalse();
        assertThat(EventoBitacora.esSensible("cliente")).isFalse();
        assertThat(EventoBitacora.esSensible(null)).isFalse();
    }

    @Test
    void sinCambiosNoHayNadaQueOcultar() {
        assertThat(evento(null).cambios()).isNull();
        Map<String, Object> cambios = Map.of("nombre", "x");
        assertThat(evento(cambios).cambios()).isSameAs(cambios);
    }

    private static EventoBitacora evento(Map<String, Object> cambios) {
        return new EventoBitacora(1L, "ADMINISTRADOR", AccionSensible.PARAMETRO_MODIFICADO, true, "Prueba",
                "config", 1L, cambios);
    }
}
