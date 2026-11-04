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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.01 Iniciar sesión con contraseña, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class InicioSesionIntegracionTest {

    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String CONTRASENA = "Clave2026segura";
    static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void usuarios() {
        crearUsuario("supervisora", 2, true);
        crearUsuario("admin.inactivo", 1, false);
        crearUsuario("cajero1", 3, true);
    }

    // Criterio 1

    @Test
    void elSupervisorEntraYRecibeLosPermisosDeSuRol() throws Exception {
        HttpResponse<String> respuesta = login("supervisora", CONTRASENA);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body())
                .contains("\"rol\":\"SUPERVISOR\"", "\"rolNombre\":\"Supervisor\"")
                .contains("REPORTE_CONSULTAR", "FACTURA_ANULAR")
                .doesNotContain("USUARIO_ADMINISTRAR", "PRODUCTO_EDITAR");
        assertThat(jdbc.queryForObject(
                "SELECT ultimo_acceso IS NOT NULL FROM usuario WHERE username = 'supervisora'", Boolean.class)).isTrue();
    }

    @Test
    void elAdministradorInicialSeCreaConContrasenaTemporalYPuedeEntrar() throws Exception {
        var admin = jdbc.queryForMap(
                "SELECT rol_id, credencial_hash, credencial_temporal, activo FROM usuario WHERE username = 'admin'");
        assertThat(((Number) admin.get("rol_id")).intValue()).isEqualTo(1);
        assertThat((String) admin.get("credencial_hash")).startsWith("$argon2id$").doesNotContain(CONTRASENA_ADMIN);
        assertThat(admin.get("credencial_temporal")).isEqualTo(true);
        assertThat(admin.get("activo")).isEqualTo(true);

        HttpResponse<String> respuesta = login("admin", CONTRASENA_ADMIN);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body())
                .contains("\"rol\":\"ADMIN\"", "\"credencialTemporal\":true")
                .contains("USUARIO_ADMINISTRAR", "CONFIGURACION_MODIFICAR");
    }

    @Test
    void laSesionViajaEnUnaCookieProtegidaYSirveEnLasSiguientesPeticiones() throws Exception {
        HttpResponse<String> respuesta = login("supervisora", CONTRASENA);
        String setCookie = respuesta.headers().firstValue("Set-Cookie").orElseThrow();
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));

        assertThat(setCookie).startsWith("POS_SESION=").contains("HttpOnly", "SameSite=Strict");
        assertThat(respuesta.body()).doesNotContain(cookie.substring("POS_SESION=".length()));

        HttpResponse<String> sesion = get("/api/auth/sesion", cookie);
        assertThat(sesion.statusCode()).isEqualTo(200);
        assertThat(sesion.body()).contains("\"username\":\"supervisora\"");
    }

    @Test
    void sinCookieOConUnTokenInventadoNoHaySesion() throws Exception {
        assertThat(get("/api/auth/sesion", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/auth/sesion", "POS_SESION=inventado").statusCode()).isEqualTo(401);
    }

    // Criterios 2 y 3

    @Test
    void usuarioOContrasenaIncorrectosDanElMismoMensajeGenerico() throws Exception {
        HttpResponse<String> contrasenaMala = login("supervisora", "OtraClave99");
        HttpResponse<String> usuarioInexistente = login("no.existe", CONTRASENA);

        assertThat(contrasenaMala.statusCode()).isEqualTo(401);
        assertThat(usuarioInexistente.statusCode()).isEqualTo(401);
        assertThat(contrasenaMala.body()).contains(MENSAJE_GENERICO).isEqualTo(usuarioInexistente.body());
        assertThat(contrasenaMala.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(usuarioInexistente.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void unUsuarioDesactivadoNoPuedeEntrar() throws Exception {
        HttpResponse<String> respuesta = login("admin.inactivo", CONTRASENA);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.body()).contains(MENSAJE_GENERICO);
        assertThat(respuesta.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void elCajeroNoEntraConContrasenaPorqueUsaPin() throws Exception {
        HttpResponse<String> respuesta = login("cajero1", CONTRASENA);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.body()).contains(MENSAJE_GENERICO);
    }

    @Test
    void losDatosVaciosSeRechazanSinConsultarCredenciales() throws Exception {
        assertThat(login("", "").statusCode()).isEqualTo(400);
    }

    // Criterio 5

    @Test
    void cadaInicioDeSesionExitosoOFallidoQuedaEnLaBitacora() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'supervisora'", Long.class);
        int exitososAntes = eventos(id, true);
        int fallidosAntes = eventos(id, false);
        int inexistentesAntes = jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'INICIO_SESION' AND NOT exitoso AND usuario_id IS NULL""", Integer.class);

        login("supervisora", CONTRASENA);
        login("supervisora", "OtraClave99");
        login("Clave2026segura", "x");

        assertThat(eventos(id, true)).isEqualTo(exitososAntes + 1);
        assertThat(eventos(id, false)).isEqualTo(fallidosAntes + 1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'INICIO_SESION' AND NOT exitoso AND usuario_id IS NULL""", Integer.class))
                .isEqualTo(inexistentesAntes + 1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE descripcion LIKE '%OtraClave99%' OR descripcion LIKE '%Clave2026segura%'""", Integer.class))
                .isZero();
    }

    @Test
    void laAplicacionSoloPuedeLeerYAgregarEnLaBitacora() {
        List<String> privilegios = jdbc.queryForList("""
                SELECT privilege_type FROM information_schema.role_table_grants
                WHERE grantee = 'pos_app' AND table_name = 'bitacora'""", String.class);
        assertThat(privilegios).containsExactlyInAnyOrder("SELECT", "INSERT");
    }

    private int eventos(long usuarioId, boolean exitoso) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'INICIO_SESION' AND usuario_id = ? AND exitoso = ?""",
                Integer.class, usuarioId, exitoso);
    }

    private void crearUsuario(String username, int rolId, boolean activo) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal, activo)
                VALUES (?, ?, ?, ?, false, ?) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(CONTRASENA), activo);
    }

    private HttpResponse<String> login(String username, String contrasena) throws Exception {
        String cuerpo = "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/login"))
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
