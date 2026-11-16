package gt.supermercado.pos.compartido;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los errores de la petición que Spring MVC clasifica (método no admitido, ruta inexistente, contenido no admitido)
 * responden con su código HTTP y el formato único de la API, no con 500.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ErroresHttpIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ErroresHttpIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        HttpResponse<String> login = enviar("/api/auth/login", "POST",
                "{\"username\":\"admin\",\"contrasena\":\"%s\"}".formatted(CONTRASENA_ADMIN), "application/json", null);
        String setCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("POS_SESION=")).findFirst().orElseThrow();
        cookieAdmin = setCookie.substring(0, setCookie.indexOf(';'));
    }

    @Test
    void unMetodoNoAdmitidoResponde405() throws Exception {
        HttpResponse<String> respuesta = enviar("/api/cajas/1", "DELETE", null, null, cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(405);
        assertThat(respuesta.body()).contains("METODO_NO_PERMITIDO");
    }

    @Test
    void unaRutaInexistenteResponde404() throws Exception {
        HttpResponse<String> respuesta = enviar("/api/no-existe", "GET", null, null, cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).contains("RECURSO_NO_ENCONTRADO");
    }

    @Test
    void unContenidoNoAdmitidoResponde415() throws Exception {
        HttpResponse<String> respuesta = enviar("/api/cajas", "POST", "nombre=Caja", "text/plain", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(415);
        assertThat(respuesta.body()).contains("CONTENIDO_NO_ADMITIDO");
    }

    @Test
    void sinSesionSigueRespondiendo401() throws Exception {
        assertThat(enviar("/api/cajas/1", "DELETE", null, null, null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> enviar(String ruta, String metodo, String cuerpo, String tipo, String cookie)
            throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (tipo != null) {
            peticion.header("Content-Type", tipo);
        }
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
