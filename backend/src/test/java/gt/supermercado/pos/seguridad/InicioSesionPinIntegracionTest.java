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

/** HU-01.02 Iniciar sesión con PIN (cajeros), contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class InicioSesionPinIntegracionTest {

    static final String PIN = "4821";
    static final String MENSAJE_GENERICO = "Usuario o PIN incorrectos";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void usuarios() {
        crearUsuario("cajera.pin", 3, PIN, true);
        crearUsuario("cajero.inactivo", 3, PIN, false);
        crearUsuario("supervisor.pin", 2, "Clave2026segura", true);
    }

    // Criterios 1 y 6

    @Test
    void elCajeroEntraConSuPinYRecibeLosPermisosDeCajero() throws Exception {
        HttpResponse<String> respuesta = loginPin("cajera.pin", PIN);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Set-Cookie").orElseThrow())
                .startsWith("POS_SESION=").contains("HttpOnly", "SameSite=Strict");
        assertThat(respuesta.body())
                .contains("\"rol\":\"CAJERO\"", "\"rolNombre\":\"Cajero\"")
                .contains("VENTA_REGISTRAR", "CAJA_OPERAR_PROPIA")
                .doesNotContain("FACTURA_ANULAR", "REPORTE_CONSULTAR");
    }

    @Test
    void elPinSeGuardaConElMismoHashSeguroQueLasContrasenas() {
        String hash = jdbc.queryForObject(
                "SELECT credencial_hash FROM usuario WHERE username = 'cajera.pin'", String.class);

        assertThat(hash).startsWith("$argon2id$").doesNotContain(PIN);
    }

    // Criterio 2

    @Test
    void unPinQueNoTieneDe4A6DigitosSeRechaza() throws Exception {
        assertThat(loginPin("cajera.pin", "123").statusCode()).isEqualTo(400);
        assertThat(loginPin("cajera.pin", "1234567").statusCode()).isEqualTo(400);
        assertThat(loginPin("cajera.pin", "12a4").statusCode()).isEqualTo(400);
        assertThat(loginPin("cajera.pin", "").statusCode()).isEqualTo(400);
    }

    // Criterios 4 y 5

    @Test
    void usuarioOPinIncorrectosDanElMismoMensajeGenerico() throws Exception {
        HttpResponse<String> pinMalo = loginPin("cajera.pin", "9999");
        HttpResponse<String> usuarioInexistente = loginPin("no.existe", PIN);

        assertThat(pinMalo.statusCode()).isEqualTo(401);
        assertThat(usuarioInexistente.statusCode()).isEqualTo(401);
        assertThat(pinMalo.body()).contains(MENSAJE_GENERICO).isEqualTo(usuarioInexistente.body());
        assertThat(pinMalo.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void unCajeroDesactivadoNoPuedeEntrar() throws Exception {
        HttpResponse<String> respuesta = loginPin("cajero.inactivo", PIN);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.body()).contains(MENSAJE_GENERICO);
        assertThat(respuesta.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void unSupervisorNoEntraConPinPorqueUsaContrasena() throws Exception {
        HttpResponse<String> respuesta = loginPin("supervisor.pin", PIN);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.body()).contains(MENSAJE_GENERICO);
    }

    // Criterio 7

    @Test
    void cadaInicioDeSesionConPinExitosoOFallidoQuedaEnLaBitacora() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'cajera.pin'", Long.class);
        int exitososAntes = eventos(id, true);
        int fallidosAntes = eventos(id, false);

        loginPin("cajera.pin", PIN);
        loginPin("cajera.pin", "9999");

        assertThat(eventos(id, true)).isEqualTo(exitososAntes + 1);
        assertThat(eventos(id, false)).isEqualTo(fallidosAntes + 1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE descripcion LIKE '%4821%' OR descripcion LIKE '%9999%'""",
                Integer.class)).isZero();
    }

    private int eventos(long usuarioId, boolean exitoso) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'INICIO_SESION' AND usuario_id = ? AND exitoso = ? AND descripcion LIKE '%PIN%'""",
                Integer.class, usuarioId, exitoso);
    }

    private void crearUsuario(String username, int rolId, String credencial, boolean activo) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal, activo)
                VALUES (?, ?, ?, ?, false, ?) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial), activo);
    }

    private HttpResponse<String> loginPin(String username, String pin) throws Exception {
        String cuerpo = "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(username, pin);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/login-pin"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
