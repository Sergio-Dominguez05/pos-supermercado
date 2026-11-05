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

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.13 Cambiar contraseña o PIN propio, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CambioCredencialIntegracionTest {

    static final String CONTRASENA = "Clave2026segura";
    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    // Criterios 1 y 2: actual, nueva y confirmación; la nueva cumple su regla

    @Test
    void elSupervisorCambiaSuContrasenaYDejaDeSerTemporal() throws Exception {
        crearUsuario("cc.super", 2, CONTRASENA);
        String cookie = login("cc.super", CONTRASENA);

        HttpResponse<String> respuesta = cambiar(cookie, CONTRASENA, "NuevaClave2027", "NuevaClave2027");

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT credencial_temporal FROM usuario WHERE username = 'cc.super'",
                Boolean.class)).isFalse();
        assertThat(loginEstado("cc.super", "NuevaClave2027")).isEqualTo(200);
        assertThat(loginEstado("cc.super", CONTRASENA)).isEqualTo(401);
    }

    @Test
    void elCajeroCambiaSuPin() throws Exception {
        crearUsuario("cc.cajera", 3, PIN);
        String cookie = loginPin("cc.cajera", PIN);

        assertThat(cambiar(cookie, PIN, "907312", "907312").statusCode()).isEqualTo(204);

        assertThat(loginPinEstado("cc.cajera", "907312")).isEqualTo(200);
        assertThat(loginPinEstado("cc.cajera", PIN)).isEqualTo(401);
    }

    @Test
    void laNuevaDebeCumplirLaReglaDeSuTipo() throws Exception {
        crearUsuario("cc.reglas.super", 2, CONTRASENA);
        crearUsuario("cc.reglas.cajero", 3, PIN);

        HttpResponse<String> contrasenaDebil = cambiar(login("cc.reglas.super", CONTRASENA), CONTRASENA, "soloLetras", "soloLetras");
        HttpResponse<String> pinConLetras = cambiar(loginPin("cc.reglas.cajero", PIN), PIN, "Clave2027", "Clave2027");

        assertThat(contrasenaDebil.statusCode()).isEqualTo(422);
        assertThat(contrasenaDebil.body()).contains("CONTRASENA_INVALIDA", "al menos 8 caracteres");
        assertThat(pinConLetras.statusCode()).isEqualTo(422);
        assertThat(pinConLetras.body()).contains("PIN_INVALIDO", "de 4 a 6 dígitos");
    }

    @Test
    void laNuevaDebeSerDistintaDeLaActual() throws Exception {
        crearUsuario("cc.igual", 2, CONTRASENA);

        HttpResponse<String> respuesta = cambiar(login("cc.igual", CONTRASENA), CONTRASENA, CONTRASENA, CONTRASENA);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("IGUAL_A_LA_ACTUAL", "La nueva contraseña debe ser distinta de la actual.");
    }

    // Criterio 3: actual incorrecta o confirmación distinta, sin cambio y con mensaje

    @Test
    void conLaActualIncorrectaNoSeHaceElCambio() throws Exception {
        crearUsuario("cc.actual", 2, CONTRASENA);
        String hashAntes = hash("cc.actual");

        HttpResponse<String> respuesta = cambiar(login("cc.actual", CONTRASENA), "OtraClave99", "NuevaClave2027", "NuevaClave2027");

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CREDENCIAL_ACTUAL_INCORRECTA", "La contraseña actual es incorrecta.");
        assertThat(hash("cc.actual")).isEqualTo(hashAntes);
    }

    @Test
    void siLaConfirmacionNoCoincideNoSeHaceElCambio() throws Exception {
        crearUsuario("cc.confirmacion", 3, PIN);
        String hashAntes = hash("cc.confirmacion");

        HttpResponse<String> respuesta = cambiar(loginPin("cc.confirmacion", PIN), PIN, "907312", "907313");

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("CONFIRMACION_DIFERENTE", "La confirmación no coincide con el nuevo PIN.");
        assertThat(hash("cc.confirmacion")).isEqualTo(hashAntes);
    }

    // Criterio 4: queda en la bitácora sin guardar la credencial

    @Test
    void elCambioYElRechazoQuedanEnLaBitacoraSinLaCredencial() throws Exception {
        long id = crearUsuario("cc.bitacora", 2, CONTRASENA);
        String cookie = login("cc.bitacora", CONTRASENA);

        cambiar(cookie, "OtraClave99", "NuevaClave2027", "NuevaClave2027");
        cambiar(cookie, CONTRASENA, "NuevaClave2027", "NuevaClave2027");

        assertThat(jdbc.queryForObject("""
                SELECT cambios->>'credencial' FROM bitacora
                WHERE accion = 'CREDENCIAL_CAMBIADA' AND exitoso AND usuario_id = ?""", String.class, id))
                .isEqualTo("modificado");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'CREDENCIAL_CAMBIADA' AND NOT exitoso AND usuario_id = ?""", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE descripcion ~ '(Clave2026segura|NuevaClave2027|OtraClave99)'
                   OR cambios::text ~ '(Clave2026segura|NuevaClave2027|OtraClave99)'""", Integer.class)).isZero();
    }

    // Datos inválidos y sesión

    @Test
    void sinSesionNoSePuedeCambiar() throws Exception {
        assertThat(cambiar(null, CONTRASENA, "NuevaClave2027", "NuevaClave2027").statusCode()).isEqualTo(401);
    }

    @Test
    void faltanDatos() throws Exception {
        crearUsuario("cc.vacio", 2, CONTRASENA);

        assertThat(cambiar(login("cc.vacio", CONTRASENA), CONTRASENA, "", "").statusCode()).isEqualTo(400);
    }

    private long crearUsuario(String username, int rolId, String credencial) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash)
                VALUES (?, ?, ?, ?) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String hash(String username) {
        return jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE username = ?", String.class, username);
    }

    private HttpResponse<String> cambiar(String cookie, String actual, String nueva, String confirmacion) throws Exception {
        String cuerpo = "{\"actual\":\"%s\",\"nueva\":\"%s\",\"confirmacion\":\"%s\"}".formatted(actual, nueva, confirmacion);
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/credencial"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String username, String contrasena) throws Exception {
        return cookie(enviarLogin("/api/auth/login", "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena)));
    }

    private String loginPin(String username, String pin) throws Exception {
        return cookie(enviarLogin("/api/auth/login-pin", "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(username, pin)));
    }

    private int loginEstado(String username, String contrasena) throws Exception {
        return enviarLogin("/api/auth/login", "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena))
                .statusCode();
    }

    private int loginPinEstado(String username, String pin) throws Exception {
        return enviarLogin("/api/auth/login-pin", "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(username, pin)).statusCode();
    }

    private static String cookie(HttpResponse<String> login) {
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> enviarLogin(String ruta, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
