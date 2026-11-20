package gt.supermercado.pos.seguridad;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-01.15 Cambio obligatorio de contraseña o PIN temporal, contra PostgreSQL real: con una credencial temporal (usuario
 * nuevo o restablecido) el servidor solo deja consultar la sesión, cambiarla y salir.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CredencialTemporalIntegracionTest {

    static final String TEMPORAL = "Temporal2026";
    static final String PIN_TEMPORAL = "4821";
    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void unUsuarioNuevoDebeCambiarSuContrasenaAntesDeHacerCualquierOtraCosa() throws Exception {   // crit. 1 y 2
        String admin = login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
        String username = "ct.super" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 100_000;
        assertThat(enviar("/api/usuarios", "POST", """
                {"nombreCompleto":"Supervisora nueva","username":"%s","rol":"SUPERVISOR","credencialTemporal":"%s"}"""
                .formatted(username, TEMPORAL), admin).statusCode()).isEqualTo(201);

        HttpResponse<String> entrada = enviar("/api/auth/login", "POST",
                "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, TEMPORAL), null);
        assertThat(entrada.statusCode()).isEqualTo(200);
        assertThat(entrada.body()).contains("\"credencialTemporal\":true");
        String sesion = cookieDe(entrada);

        // Crit. 2: nada más funciona, aunque tenga el permiso.
        for (String[] intento : new String[][]{{"GET", "/api/reportes/filtros"}, {"GET", "/api/facturas/historial"},
                {"GET", "/api/productos?pagina=0"}, {"GET", "/api/bitacora"}}) {
            HttpResponse<String> respuesta = enviar(intento[1], intento[0], null, sesion);
            assertThat(respuesta.statusCode()).as(intento[1]).isEqualTo(403);
            assertThat(respuesta.body()).contains("CREDENCIAL_TEMPORAL",
                    "Su contraseña es temporal: debe cambiarla antes de continuar.");
        }
        // Lo que sí puede hacer: ver su sesión y cambiar la credencial.
        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).body()).contains("\"credencialTemporal\":true");

        // Crit. 3: la nueva cumple su regla y es distinta de la temporal.
        assertThat(cambiar(sesion, TEMPORAL, TEMPORAL).body()).contains("IGUAL_A_LA_ACTUAL");
        assertThat(cambiar(sesion, TEMPORAL, "corta").statusCode()).isEqualTo(422);
        assertThat(enviar("/api/reportes/filtros", "GET", null, sesion).statusCode()).isEqualTo(403);

        assertThat(cambiar(sesion, TEMPORAL, "Definitiva2027").statusCode()).isEqualTo(204);

        // Con la misma sesión, ya puede trabajar: la marca se lee en cada petición.
        assertThat(enviar("/api/reportes/filtros", "GET", null, sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).body()).contains("\"credencialTemporal\":false");
    }

    @Test
    void unCajeroConPinRestablecidoDebeCambiarloYPuedeSalir() throws Exception {   // crit. 1 y 2, usuario restablecido
        String username = "ct.cajero" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 100_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Cajero restablecido', 3, ?, false)""", username, codificador.encode("5555"));
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
        String admin = login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
        assertThat(enviar("/api/usuarios/" + id + "/credencial", "POST",
                "{\"credencialTemporal\":\"%s\"}".formatted(PIN_TEMPORAL), admin).statusCode()).isIn(200, 204);

        String sesion = login("/api/auth/login-pin", username, "pin", PIN_TEMPORAL);

        HttpResponse<String> venta = enviar("/api/turnos/actual", "GET", null, sesion);
        assertThat(venta.statusCode()).isEqualTo(403);
        assertThat(venta.body()).contains("Su PIN es temporal: debe cambiarlo antes de continuar.");
        assertThat(enviar("/api/auth/logout", "POST", null, sesion).statusCode()).isIn(200, 204);
        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).statusCode()).isEqualTo(401);
    }

    @Test
    void quienYaCambioSuCredencialTrabajaNormalmente() throws Exception {
        String username = "ct.normal" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 100_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor', 2, ?, false)""", username, codificador.encode("Clave2026segura"));

        String sesion = login("/api/auth/login", username, "contrasena", "Clave2026segura");

        assertThat(enviar("/api/reportes/filtros", "GET", null, sesion).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/sistema/estado", "GET", null, null).statusCode()).isNotEqualTo(403);
    }

    private HttpResponse<String> cambiar(String sesion, String actual, String nueva) throws Exception {
        return enviar("/api/auth/credencial", "PUT",
                "{\"actual\":\"%s\",\"nueva\":\"%s\",\"confirmacion\":\"%s\"}".formatted(actual, nueva, nueva), sesion);
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        HttpResponse<String> respuesta = enviar(ruta, "POST",
                "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return cookieDe(respuesta);
    }

    private static String cookieDe(HttpResponse<String> respuesta) {
        String setCookie = respuesta.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviar(String ruta, String metodo, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
