package gt.supermercado.pos.configuracion;

import gt.supermercado.pos.TestcontainersConfiguration;
import gt.supermercado.pos.configuracion.aplicacion.Parametros;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-12.13: configuración de los parámetros de caja y venta contra PostgreSQL real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + ParametrosOperacionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ParametrosOperacionIntegracionTest {

    static final String CONTRASENA_ADMIN = "Inicial2026";
    private static final String CONTRASENA = "Clave2026segura";
    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    Parametros parametros;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void restaurarParametros() throws Exception {
        jdbc.update("UPDATE parametro SET valor = '50'::jsonb WHERE clave = 'cantidad_max_linea'");
        jdbc.update("UPDATE parametro SET valor = 'false'::jsonb WHERE clave = 'cierre_ciego'");
        cookieAdmin = iniciarSesion("admin", CONTRASENA_ADMIN);
    }

    @Test
    void consultaYModificaLosParametrosConAplicacionInmediata() throws Exception {   // crit. 1 y 2
        assertThat(enviar("GET", null, cookieAdmin).body())
                .contains("\"cantidadMaximaPorLinea\":50", "\"cierreCiego\":false");

        HttpResponse<String> respuesta = enviar("PUT",
                "{\"cantidadMaximaPorLinea\":12,\"cierreCiego\":true}", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"cantidadMaximaPorLinea\":12", "\"cierreCiego\":true");
        assertThat(parametros.cantidadMaximaPorLinea()).isEqualTo(12);
        assertThat(parametros.cierreCiego()).isTrue();
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(valor) FROM parametro WHERE clave = 'cierre_ciego'",
                String.class)).isEqualTo("boolean");
    }

    @Test
    void exigeUnaCantidadEnteraPositivaYSinCambiarLosValores() throws Exception {
        HttpResponse<String> respuesta = enviar("PUT",
                "{\"cantidadMaximaPorLinea\":10000,\"cierreCiego\":true}", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CANTIDAD_MAX_LINEA_INVALIDA", "9999");
        assertThat(parametros.cantidadMaximaPorLinea()).isEqualTo(50);
        assertThat(parametros.cierreCiego()).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND NOT exitoso
                  AND entidad = 'parametros_operacion'""", Integer.class)).isGreaterThan(0);
    }

    @Test
    void soloElAdministradorPuedeConsultarOModificarLosParametros() throws Exception {
        String supervisor = "param.supervisor." + SIGUIENTE.incrementAndGet();
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, 'Supervisor parámetros', 2, ?, false)""", supervisor, codificador.encode(CONTRASENA));
        String cookieSupervisor = iniciarSesion(supervisor, CONTRASENA);

        assertThat(enviar("GET", null, cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(enviar("PUT", "{\"cantidadMaximaPorLinea\":10,\"cierreCiego\":true}", cookieSupervisor)
                .statusCode()).isEqualTo(403);
    }

    @Test
    void registraSoloLosValoresQueCambiaron() throws Exception {
        enviar("PUT", "{\"cantidadMaximaPorLinea\":25,\"cierreCiego\":false}", cookieAdmin);

        String cambios = jdbc.queryForObject("""
                SELECT cambios::text FROM bitacora
                WHERE accion = 'PARAMETRO_MODIFICADO' AND exitoso AND entidad = 'parametros_operacion'
                ORDER BY secuencia DESC LIMIT 1""", String.class);
        assertThat(cambios).contains("cantidadMaximaPorLinea", "50", "25").doesNotContain("cierreCiego");

        long antes = jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class);
        enviar("PUT", "{\"cantidadMaximaPorLinea\":25,\"cierreCiego\":false}", cookieAdmin);
        assertThat(jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class)).isEqualTo(antes);
    }

    private String iniciarSesion(String username, String contrasena) throws Exception {
        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"%s\",\"contrasena\":\"%s\"}"
                        .formatted(username, contrasena))).build(), HttpResponse.BodyHandlers.ofString());
        String setCookie = respuesta.headers().allValues("Set-Cookie").stream()
                .filter(valor -> valor.startsWith("POS_SESION=")).findFirst().orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviar(String metodo, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/configuracion/parametros"))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Cookie", cookie);
        if (cuerpo != null) peticion.header("Content-Type", "application/json");
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
