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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.08 Activar y desactivar usuario, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class EstadoUsuariosIntegracionTest {

    static final String CONTRASENA = "Clave2026segura";
    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String cookieAdmin;

    @BeforeEach
    void sesionDelAdministrador() throws Exception {
        cookieAdmin = login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
    }

    // Criterio 1

    @Test
    void unUsuarioDesactivadoNoPuedeIniciarSesion() throws Exception {
        long id = crearUsuario("es.cajera", 3, PIN);

        HttpResponse<String> respuesta = accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"activo\":false");
        HttpResponse<String> login = enviarJson("/api/auth/login-pin", "{\"username\":\"es.cajera\",\"pin\":\"" + PIN + "\"}");
        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).contains("Usuario o PIN incorrectos");
    }

    @Test
    void laSesionAbiertaDeUnUsuarioDesactivadoDejaDeValer() throws Exception {
        long id = crearUsuario("es.sesion", 2, CONTRASENA);
        String cookie = login("/api/auth/login", "es.sesion", "contrasena", CONTRASENA);
        assertThat(get("/api/auth/sesion", cookie).statusCode()).isEqualTo(200);

        accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);

        assertThat(get("/api/auth/sesion", cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void alReactivarloVuelveAPoderIniciarSesion() throws Exception {
        long id = crearUsuario("es.reactivado", 2, CONTRASENA);
        accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);

        HttpResponse<String> respuesta = accion("/api/usuarios/" + id + "/activar", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"activo\":true");
        assertThat(enviarJson("/api/auth/login",
                "{\"username\":\"es.reactivado\",\"contrasena\":\"" + CONTRASENA + "\"}").statusCode()).isEqualTo(200);
    }

    // Criterio 2

    @Test
    void desactivarNoBorraAlUsuarioNiSuHistorial() throws Exception {
        long id = crearUsuario("es.historial", 3, PIN);
        enviarJson("/api/auth/login-pin", "{\"username\":\"es.historial\",\"pin\":\"" + PIN + "\"}");
        int eventosAntes = eventosDe(id);

        accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(eventosDe(id)).isGreaterThan(eventosAntes);
        assertThat(get("/api/usuarios?activo=false", cookieAdmin).body()).contains("es.historial");
    }

    // Criterio 4

    @Test
    void elAdministradorNoPuedeDesactivarSuPropioUsuario() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);

        HttpResponse<String> respuesta = accion("/api/usuarios/" + admin + "/desactivar", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("PROPIO_USUARIO", "No puede desactivar su propio usuario.");
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, admin)).isTrue();
    }

    // Criterio 6

    @Test
    void laActivacionYLaDesactivacionQuedanEnLaBitacora() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        long id = crearUsuario("es.bitacora", 3, PIN);

        accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);
        accion("/api/usuarios/" + id + "/activar", cookieAdmin);

        Map<String, Object> desactivado = ultimo("USUARIO_DESACTIVADO", id);
        Map<String, Object> activado = ultimo("USUARIO_ACTIVADO", id);
        assertThat(desactivado.get("usuario_id")).isEqualTo(admin);
        assertThat(desactivado.get("antes")).isEqualTo("true");
        assertThat(desactivado.get("despues")).isEqualTo("false");
        assertThat(activado.get("usuario_id")).isEqualTo(admin);
        assertThat(activado.get("despues")).isEqualTo("true");
    }

    @Test
    void repetirLaMismaAccionNoRegistraNadaNuevo() throws Exception {
        long id = crearUsuario("es.repetido", 3, PIN);
        accion("/api/usuarios/" + id + "/desactivar", cookieAdmin);
        int eventos = eventosDe(id);

        assertThat(accion("/api/usuarios/" + id + "/desactivar", cookieAdmin).statusCode()).isEqualTo(200);
        assertThat(eventosDe(id)).isEqualTo(eventos);
    }

    // Permisos y datos inválidos

    @Test
    void soloQuienTienePermisoDeAdministrarUsuariosPuedeActivarODesactivar() throws Exception {
        long id = crearUsuario("es.objetivo", 3, PIN);
        crearUsuario("es.vigilante", 2, CONTRASENA);
        String cookieSupervisor = login("/api/auth/login", "es.vigilante", "contrasena", CONTRASENA);

        assertThat(accion("/api/usuarios/" + id + "/desactivar", cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(accion("/api/usuarios/" + id + "/activar", cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(accion("/api/usuarios/" + id + "/desactivar", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void unUsuarioQueNoExisteDa404() throws Exception {
        assertThat(accion("/api/usuarios/999999/desactivar", cookieAdmin).statusCode()).isEqualTo(404);
        assertThat(accion("/api/usuarios/999999/activar", cookieAdmin).statusCode()).isEqualTo(404);
    }

    private Map<String, Object> ultimo(String accion, long id) {
        return jdbc.queryForMap("""
                SELECT usuario_id, cambios->'activo'->>'antes' AS antes, cambios->'activo'->>'despues' AS despues
                FROM bitacora WHERE accion = ? AND entidad_id = ? AND exitoso ORDER BY id DESC LIMIT 1""", accion, id);
    }

    private int eventosDe(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE entidad = 'usuario' AND entidad_id = ?", Integer.class, id);
    }

    private long crearUsuario(String username, int rolId, String credencial) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        HttpResponse<String> respuesta = enviarJson(ruta, "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor));
        String setCookie = respuesta.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    /** POST sin cuerpo con la cookie de sesión (activar, desactivar). */
    private HttpResponse<String> accion(String ruta, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** POST con cuerpo JSON y sin sesión (inicio de sesión). */
    private HttpResponse<String> enviarJson(String ruta, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String ruta, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
