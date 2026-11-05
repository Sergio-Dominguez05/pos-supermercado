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

/** HU-01.14 Restablecer contraseña o PIN de un usuario, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class RestablecimientoCredencialIntegracionTest {

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
        cookieAdmin = cookie(login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN));
    }

    // Criterio 1: contraseña o PIN temporal según el rol

    @Test
    void alSupervisorSeLeAsignaUnaContrasenaTemporalYEntraConElla() throws Exception {
        long id = crearUsuario("rc.super", 2, CONTRASENA, false);

        HttpResponse<String> respuesta = restablecer(id, "Temporal2026", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"tipo\":\"CONTRASENA\"", "\"temporal\":true")
                .doesNotContain("Temporal2026", "argon2");
        assertThat(temporal(id)).isTrue();
        assertThat(login("/api/auth/login", "rc.super", "contrasena", "Temporal2026").statusCode()).isEqualTo(200);
        assertThat(login("/api/auth/login", "rc.super", "contrasena", CONTRASENA).statusCode()).isEqualTo(401);
    }

    @Test
    void alCajeroSeLeAsignaUnPinTemporalYEntraConEl() throws Exception {
        long id = crearUsuario("rc.cajera", 3, PIN, false);

        HttpResponse<String> respuesta = restablecer(id, "730415", cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"tipo\":\"PIN\"");
        assertThat(login("/api/auth/login-pin", "rc.cajera", "pin", "730415").statusCode()).isEqualTo(200);
    }

    @Test
    void laCredencialTemporalDebeCumplirLaReglaDelRol() throws Exception {
        long supervisor = crearUsuario("rc.reglas.super", 2, CONTRASENA, false);
        long cajero = crearUsuario("rc.reglas.cajero", 3, PIN, false);
        String hashAntes = hash(supervisor);

        HttpResponse<String> pinASupervisor = restablecer(supervisor, "1234", cookieAdmin);
        HttpResponse<String> contrasenaACajero = restablecer(cajero, "Temporal2026", cookieAdmin);

        assertThat(pinASupervisor.statusCode()).isEqualTo(422);
        assertThat(pinASupervisor.body()).contains("CONTRASENA_INVALIDA");
        assertThat(contrasenaACajero.statusCode()).isEqualTo(422);
        assertThat(contrasenaACajero.body()).contains("PIN_INVALIDO");
        assertThat(hash(supervisor)).isEqualTo(hashAntes);
    }

    // Criterio 2: solo el Administrador

    @Test
    void soloElAdministradorPuedeRestablecer() throws Exception {
        long id = crearUsuario("rc.objetivo", 3, PIN, false);
        crearUsuario("rc.supervisor", 2, CONTRASENA, false);
        crearUsuario("rc.cajero.actor", 3, PIN, false);
        String supervisor = cookie(login("/api/auth/login", "rc.supervisor", "contrasena", CONTRASENA));
        String cajero = cookie(login("/api/auth/login-pin", "rc.cajero.actor", "pin", PIN));
        String hashAntes = hash(id);

        assertThat(restablecer(id, "730415", supervisor).statusCode()).isEqualTo(403);
        assertThat(restablecer(id, "730415", cajero).statusCode()).isEqualTo(403);
        assertThat(restablecer(id, "730415", null).statusCode()).isEqualTo(401);
        assertThat(hash(id)).isEqualTo(hashAntes);
    }

    @Test
    void elPermisoDeRestablecerEsExclusivoDelAdministradorEnLaMatriz() {
        assertThat(jdbc.queryForList(
                "SELECT rol_id FROM rol_permiso WHERE permiso = 'CREDENCIAL_RESTABLECER'", Integer.class))
                .containsExactly(1);
    }

    // Criterio 3: el Administrador nunca ve la credencial actual

    @Test
    void ningunaRespuestaDeLaApiDevuelveLaCredencialNiSuHash() throws Exception {
        long id = crearUsuario("rc.oculto", 2, CONTRASENA, false);

        String lista = enviar("/api/usuarios?q=rc.oculto", "GET", null, cookieAdmin).body();
        String detalle = enviar("/api/usuarios/" + id, "GET", null, cookieAdmin).body();
        String restablecido = restablecer(id, "Temporal2026", cookieAdmin).body();

        for (String cuerpo : new String[] {lista, detalle, restablecido}) {
            assertThat(cuerpo).doesNotContain(CONTRASENA, "Temporal2026", "argon2", "credencialHash", "credencial_hash");
        }
    }

    // Criterio 4: queda en la bitácora

    @Test
    void elRestablecimientoQuedaEnLaBitacoraConElAdministradorYSinLaCredencial() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        long id = crearUsuario("rc.bitacora", 3, PIN, false);

        restablecer(id, "12", cookieAdmin);
        restablecer(id, "730415", cookieAdmin);

        Map<String, Object> exito = jdbc.queryForMap("""
                SELECT usuario_id, rol, cambios->>'credencial' AS credencial FROM bitacora
                WHERE accion = 'CREDENCIAL_RESTABLECIDA' AND exitoso AND entidad_id = ?""", id);
        assertThat(exito.get("usuario_id")).isEqualTo(admin);
        assertThat(exito.get("rol")).isEqualTo("ADMIN");
        assertThat(exito.get("credencial")).isEqualTo("modificado");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'CREDENCIAL_RESTABLECIDA' AND NOT exitoso AND entidad_id = ?""",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'CREDENCIAL_RESTABLECIDA'
                  AND (descripcion LIKE '%730415%' OR cambios::text LIKE '%730415%')""", Integer.class)).isZero();
    }

    // #136: quien tenía la credencial anterior pierde el acceso al restablecerla

    @Test
    void restablecerCierraTodasLasSesionesAbiertasDelUsuarioYNoLasDeOtros() throws Exception {
        long id = crearUsuario("rc.sesiones", 2, CONTRASENA, false);
        String primera = cookie(login("/api/auth/login", "rc.sesiones", "contrasena", CONTRASENA));
        String segunda = cookie(login("/api/auth/login", "rc.sesiones", "contrasena", CONTRASENA));
        assertThat(enviar("/api/auth/sesion", "GET", null, primera).statusCode()).isEqualTo(200);

        assertThat(restablecer(id, "Temporal2026", cookieAdmin).statusCode()).isEqualTo(200);

        assertThat(enviar("/api/auth/sesion", "GET", null, primera).statusCode()).isEqualTo(401);
        assertThat(enviar("/api/auth/sesion", "GET", null, segunda).statusCode()).isEqualTo(401);
        assertThat(enviar("/api/auth/sesion", "GET", null, cookieAdmin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion WHERE usuario_id = ? AND cerrada_en IS NULL",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT descripcion FROM bitacora WHERE accion = 'CREDENCIAL_RESTABLECIDA' AND entidad_id = ?
                ORDER BY secuencia DESC LIMIT 1""", String.class, id)).endsWith("se cerraron 2 sesiones abiertas");
        // Con la credencial nueva vuelve a entrar.
        assertThat(login("/api/auth/login", "rc.sesiones", "contrasena", "Temporal2026").statusCode()).isEqualTo(200);
    }

    @Test
    void unRestablecimientoRechazadoNoCierraLasSesiones() throws Exception {
        long id = crearUsuario("rc.rechazo", 3, PIN, false);
        String sesion = cookie(login("/api/auth/login-pin", "rc.rechazo", "pin", PIN));

        assertThat(restablecer(id, "12", cookieAdmin).statusCode()).isEqualTo(422);

        assertThat(enviar("/api/auth/sesion", "GET", null, sesion).statusCode()).isEqualTo(200);
    }

    @Test
    void unUsuarioQueNoExisteDa404() throws Exception {
        assertThat(restablecer(999999, "Temporal2026", cookieAdmin).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> restablecer(long id, String credencial, String cookie) throws Exception {
        return enviar("/api/usuarios/" + id + "/credencial", "POST",
                "{\"credencialTemporal\":\"" + credencial + "\"}", cookie);
    }

    private long crearUsuario(String username, int rolId, String credencial, boolean temporal) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial), temporal);
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private boolean temporal(long id) {
        return jdbc.queryForObject("SELECT credencial_temporal FROM usuario WHERE id = ?", Boolean.class, id);
    }

    private String hash(long id) {
        return jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE id = ?", String.class, id);
    }

    private HttpResponse<String> login(String ruta, String username, String campo, String valor) throws Exception {
        return enviar(ruta, "POST", "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null);
    }

    private static String cookie(HttpResponse<String> login) {
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
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
