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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.06 Consultar usuarios, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class ConsultaUsuariosIntegracionTest {

    static final String CONTRASENA = "Clave2026segura";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void usuarios() throws Exception {
        crearUsuario("oscar.nunez", "Óscar Núñez", 2, true, "2026-11-01 10:30:00-06");
        crearUsuario("paula.q", "Paula Quintero", 3, false, null);
        crearUsuario("caja_1", "Raúl Solís", 3, true, null);
        crearUsuario("cajax1", "Sonia Tobar", 3, true, null);
        crearUsuario("vigilante.sup", "Vigilante Supervisor", 2, true, null);
        cookieAdmin = login("admin", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
    }

    // Criterios 1 y 4

    @Test
    void laListaMuestraLosDatosDeCadaUsuarioYNuncaSuCredencial() throws Exception {
        HttpResponse<String> respuesta = listar("");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body())
                .contains("\"username\":\"oscar.nunez\"", "\"nombreCompleto\":\"Óscar Núñez\"")
                .contains("\"rol\":\"SUPERVISOR\"", "\"rolNombre\":\"Supervisor\"")
                .contains("\"activo\":false", "\"ultimoAcceso\":null", "\"ultimoAcceso\":\"2026-11-01T")
                .doesNotContain("argon2", "credencial", "hash", CONTRASENA);
    }

    // Criterio 2

    @Test
    void seBuscaPorNombreSinImportarTildesNiMayusculas() throws Exception {
        String cuerpo = listar("q=" + codificar("oscar NUÑEZ")).body();

        assertThat(cuerpo).contains("oscar.nunez").doesNotContain("paula.q", "caja_1");
    }

    @Test
    void seBuscaPorNombreDeUsuario() throws Exception {
        assertThat(listar("q=paula.Q").body()).contains("Paula Quintero").doesNotContain("oscar.nunez");
    }

    @Test
    void elGuionBajoSeBuscaLiteralYNoComoComodin() throws Exception {
        String cuerpo = listar("q=caja_1").body();

        assertThat(cuerpo).contains("\"username\":\"caja_1\"").doesNotContain("cajax1");
    }

    // Criterio 3

    @Test
    void seFiltraPorRol() throws Exception {
        String cuerpo = listar("rol=CAJERO").body();

        assertThat(cuerpo).contains("paula.q", "caja_1").doesNotContain("\"rol\":\"SUPERVISOR\"", "\"rol\":\"ADMIN\"");
    }

    @Test
    void seFiltraPorEstado() throws Exception {
        String inactivos = listar("activo=false").body();
        String activos = listar("activo=true").body();

        assertThat(inactivos).contains("paula.q").doesNotContain("\"activo\":true");
        assertThat(activos).contains("oscar.nunez").doesNotContain("\"activo\":false");
    }

    @Test
    void laBusquedaYLosFiltrosSeCombinan() throws Exception {
        String cuerpo = listar("q=" + codificar("raúl") + "&rol=CAJERO&activo=true").body();

        assertThat(cuerpo).contains("caja_1").doesNotContain("paula.q", "oscar.nunez", "cajax1");
        assertThat(listar("q=" + codificar("raúl") + "&rol=SUPERVISOR").body()).isEqualTo("[]");
    }

    @Test
    void unFiltroConUnValorQueNoExisteSeRechaza() throws Exception {
        assertThat(listar("rol=GERENTE").statusCode()).isEqualTo(400);
        assertThat(listar("activo=quizas").statusCode()).isEqualTo(400);
    }

    // Permisos validados en el servidor

    @Test
    void soloQuienTienePermisoDeAdministrarUsuariosPuedeConsultarlos() throws Exception {
        String cookieSupervisor = login("vigilante.sup", CONTRASENA);

        assertThat(get("/api/usuarios", cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(get("/api/usuarios", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> listar(String parametros) throws Exception {
        return get("/api/usuarios" + (parametros.isEmpty() ? "" : "?" + parametros), cookieAdmin);
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private void crearUsuario(String username, String nombre, int rolId, boolean activo, String ultimoAcceso) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal,
                                     activo, ultimo_acceso)
                VALUES (?, ?, ?, ?, false, ?, ?::timestamptz) ON CONFLICT (username) DO NOTHING""",
                username, nombre, rolId, codificador.encode(CONTRASENA), activo, ultimoAcceso);
    }

    private String login(String username, String contrasena) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena)))
                .build();
        String setCookie = http.send(peticion, HttpResponse.BodyHandlers.ofString())
                .headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> get(String ruta, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
