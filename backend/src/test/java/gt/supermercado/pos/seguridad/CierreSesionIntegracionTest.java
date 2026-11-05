package gt.supermercado.pos.seguridad;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.16 Cerrar sesión, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CierreSesionIntegracionTest {

    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void cajero() {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('cs.cajera', 'Cajera que sale', 3, ?, false) ON CONFLICT (username) DO NOTHING""",
                codificador.encode(PIN));
    }

    // Criterio 1

    @Test
    void cerrarSesionTerminaLaSesionYBorraLaCookie() throws Exception {
        String cookie = login();

        HttpResponse<String> respuesta = logout(cookie);

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(respuesta.headers().firstValue("Set-Cookie").orElseThrow())
                .startsWith("POS_SESION=;").contains("Max-Age=0", "HttpOnly", "SameSite=Strict");
    }

    @Test
    void elTokenDejaDeValerAunqueAlguienGuardeLaCookie() throws Exception {
        String cookie = login();

        logout(cookie);

        assertThat(sesion(cookie).statusCode()).isEqualTo(401);
        assertThat(logout(cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void soloSeCierraLaSesionDeEsteEquipo() throws Exception {
        String esteEquipo = login();
        String otroEquipo = login();

        logout(esteEquipo);

        assertThat(sesion(esteEquipo).statusCode()).isEqualTo(401);
        assertThat(sesion(otroEquipo).statusCode()).isEqualTo(200);
    }

    @Test
    void despuesDeCerrarSesionSePuedeVolverAEntrar() throws Exception {
        logout(login());

        assertThat(sesion(login()).statusCode()).isEqualTo(200);
    }

    @Test
    void sinSesionNoHayNadaQueCerrar() throws Exception {
        assertThat(logout(null).statusCode()).isEqualTo(401);
    }

    // Criterio 4

    @Test
    void elCierreDeSesionQuedaEnLaBitacora() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'cs.cajera'", Long.class);
        int antes = cierres(id);

        logout(login());

        assertThat(cierres(id)).isEqualTo(antes + 1);
        assertThat(jdbc.queryForObject("""
                SELECT rol FROM bitacora WHERE accion = 'CIERRE_SESION' AND usuario_id = ? ORDER BY id DESC LIMIT 1""",
                String.class, id)).isEqualTo("CAJERO");
    }

    private int cierres(long usuarioId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'CIERRE_SESION' AND exitoso AND usuario_id = ?""",
                Integer.class, usuarioId);
    }

    private String login() throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/login-pin"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"cs.cajera\",\"pin\":\"" + PIN + "\"}"))
                .build();
        String setCookie = http.send(peticion, HttpResponse.BodyHandlers.ofString())
                .headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> logout(String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sesion(String cookie) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/sesion"))
                .header("Cookie", cookie).GET().build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
