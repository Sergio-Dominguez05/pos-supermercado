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

/** HU-01.05 Crear usuario, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class CreacionUsuariosIntegracionTest {

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
        cookieAdmin = cookie(post("/api/auth/login",
                "{\"username\":\"admin\",\"contrasena\":\"" + InicioSesionIntegracionTest.CONTRASENA_ADMIN + "\"}", null));
    }

    // Criterios 1, 2, 5 y 6

    @Test
    void elAdministradorCreaUnSupervisorActivoConContrasenaTemporalCifrada() throws Exception {
        HttpResponse<String> respuesta = crear("Ana López", "  Ana.Supervisora ", "SUPERVISOR", "Temporal2026");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(respuesta.body())
                .contains("\"username\":\"ana.supervisora\"", "\"rol\":\"SUPERVISOR\"")
                .contains("\"activo\":true", "\"credencialTemporal\":true")
                .doesNotContain("Temporal2026", "argon2");

        Map<String, Object> guardado = jdbc.queryForMap("""
                SELECT nombre_completo, credencial_hash, credencial_temporal, activo
                FROM usuario WHERE username = 'ana.supervisora'""");
        assertThat(guardado.get("nombre_completo")).isEqualTo("Ana López");
        assertThat((String) guardado.get("credencial_hash")).startsWith("$argon2id$").doesNotContain("Temporal2026");
        assertThat(guardado.get("credencial_temporal")).isEqualTo(true);
        assertThat(guardado.get("activo")).isEqualTo(true);
    }

    @Test
    void elUsuarioCreadoPuedeIniciarSesionSinImportarMayusculas() throws Exception {
        crear("Bruno Paz", "bruno.paz", "ADMIN", "Temporal2026");

        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"Bruno.Paz\",\"contrasena\":\"Temporal2026\"}", null);

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"username\":\"bruno.paz\"", "\"credencialTemporal\":true");
    }

    // Criterio 3

    @Test
    void elCajeroSeCreaConPinTemporalYEntraConEl() throws Exception {
        HttpResponse<String> respuesta = crear("Carla Ruiz", "carla.ruiz", "CAJERO", "5307");

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE username = 'carla.ruiz'", String.class))
                .startsWith("$argon2id$");
        assertThat(post("/api/auth/login-pin", "{\"username\":\"carla.ruiz\",\"pin\":\"5307\"}", null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void laCredencialTemporalDebeCumplirLaReglaDeSuRol() throws Exception {
        HttpResponse<String> contrasenaDebil = crear("Diego", "diego.debil", "SUPERVISOR", "soloLetras");
        HttpResponse<String> pinEnSupervisor = crear("Diego", "diego.pin", "SUPERVISOR", "4821");
        HttpResponse<String> pinCorto = crear("Elsa", "elsa.corto", "CAJERO", "12");
        HttpResponse<String> contrasenaEnCajero = crear("Elsa", "elsa.clave", "CAJERO", "Temporal2026");

        assertThat(contrasenaDebil.statusCode()).isEqualTo(422);
        assertThat(contrasenaDebil.body()).contains("CONTRASENA_INVALIDA", "al menos 8 caracteres");
        assertThat(pinEnSupervisor.statusCode()).isEqualTo(422);
        assertThat(pinCorto.statusCode()).isEqualTo(422);
        assertThat(pinCorto.body()).contains("PIN_INVALIDO", "de 4 a 6 dígitos");
        assertThat(contrasenaEnCajero.statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM usuario
                WHERE username IN ('diego.debil', 'diego.pin', 'elsa.corto', 'elsa.clave')""", Integer.class)).isZero();
    }

    @Test
    void elNombreDeUsuarioDebeTenerUnFormatoValido() throws Exception {
        HttpResponse<String> respuesta = crear("Fabio", "fa bio", "SUPERVISOR", "Temporal2026");

        assertThat(respuesta.statusCode()).isEqualTo(422);
        assertThat(respuesta.body()).contains("USERNAME_INVALIDO");
    }

    @Test
    void faltanDatosObligatorios() throws Exception {
        assertThat(post("/api/usuarios", "{\"username\":\"gina\",\"rol\":\"CAJERO\"}", cookieAdmin).statusCode())
                .isEqualTo(400);
        assertThat(crear("Gina", "gina", "GERENTE", "Temporal2026").statusCode()).isEqualTo(400);
        assertThat(post("/api/usuarios", "{no es json", cookieAdmin).statusCode()).isEqualTo(400);
    }

    // Criterio 4

    @Test
    void noSePuedeRepetirUnNombreDeUsuarioAunqueCambienLasMayusculas() throws Exception {
        assertThat(crear("Hugo", "hugo.repetido", "SUPERVISOR", "Temporal2026").statusCode()).isEqualTo(201);

        HttpResponse<String> repetido = crear("Otro Hugo", "HUGO.Repetido", "CAJERO", "1234");

        assertThat(repetido.statusCode()).isEqualTo(422);
        assertThat(repetido.body()).contains("USERNAME_DUPLICADO", "El nombre de usuario ya existe.");
        assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE username = 'hugo.repetido'", Integer.class))
                .isEqualTo(2);
    }

    // Criterio 7

    @Test
    void laCreacionQuedaEnLaBitacoraConElAdministradorQueLaHizo() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);

        crear("Iris", "iris.bitacora", "CAJERO", "9142");
        crear("Iris", "iris.bitacora", "CAJERO", "9142");

        long iris = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'iris.bitacora'", Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'USUARIO_CREADO' AND exitoso AND usuario_id = ? AND rol = 'ADMIN'
                  AND entidad = 'usuario' AND entidad_id = ?""", Integer.class, admin, iris)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'USUARIO_CREADO' AND NOT exitoso AND usuario_id = ?
                  AND descripcion LIKE '%iris.bitacora%ya existe%'""", Integer.class, admin)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE descripcion LIKE '%9142%'", Integer.class)).isZero();
    }

    // Permisos validados en el servidor

    @Test
    void soloQuienTienePermisoDeAdministrarUsuariosPuedeCrearlos() throws Exception {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('jefe.turno', 'Jefe de turno', 2, ?, false) ON CONFLICT (username) DO NOTHING""",
                codificador.encode("Clave2026segura"));
        String cookieSupervisor = cookie(post("/api/auth/login",
                "{\"username\":\"jefe.turno\",\"contrasena\":\"Clave2026segura\"}", null));
        String cuerpo = "{\"nombreCompleto\":\"Intruso\",\"username\":\"intruso\",\"rol\":\"ADMIN\","
                + "\"credencialTemporal\":\"Temporal2026\"}";

        HttpResponse<String> supervisor = post("/api/usuarios", cuerpo, cookieSupervisor);
        HttpResponse<String> anonimo = post("/api/usuarios", cuerpo, null);

        assertThat(supervisor.statusCode()).isEqualTo(403);
        assertThat(supervisor.body()).contains("ACCESO_DENEGADO");
        assertThat(anonimo.statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE username = 'intruso'", Integer.class))
                .isZero();
    }

    private HttpResponse<String> crear(String nombre, String username, String rol, String credencial) throws Exception {
        String cuerpo = "{\"nombreCompleto\":\"%s\",\"username\":\"%s\",\"rol\":\"%s\",\"credencialTemporal\":\"%s\"}"
                .formatted(nombre, username, rol, credencial);
        return post("/api/usuarios", cuerpo, cookieAdmin);
    }

    private static String cookie(HttpResponse<String> login) {
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> post(String ruta, String cuerpo, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
