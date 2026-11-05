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

/**
 * HU-01.11 Restringir acciones según permisos: lo que un usuario intente sin permiso, por cualquier medio,
 * se rechaza en el servidor y queda en la bitácora (crit. 2 y 3).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class AccesoDenegadoIntegracionTest {

    static final String CONTRASENA = "Clave2026segura";
    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void usuarios() {
        crearUsuario("ad.supervisor", 2, CONTRASENA);
        crearUsuario("ad.cajero", 3, PIN);
    }

    @Test
    void elSupervisorQueConsultaUsuariosEsRechazadoYQuedaEnLaBitacora() throws Exception {
        long supervisor = idDe("ad.supervisor");
        String cookie = login("/api/auth/login", "ad.supervisor", "contrasena", CONTRASENA);
        int antes = denegados(supervisor);

        HttpResponse<String> respuesta = enviar("/api/usuarios", "GET", null, cookie);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).contains("ACCESO_DENEGADO", "No tiene permiso para realizar esta acción.");
        assertThat(denegados(supervisor)).isEqualTo(antes + 1);
        Map<String, Object> registro = ultimo(supervisor);
        assertThat(registro.get("rol")).isEqualTo("SUPERVISOR");
        assertThat(registro.get("exitoso")).isEqualTo(false);
        assertThat(registro.get("descripcion")).isEqualTo("Acción sin permiso rechazada: GET /api/usuarios");
    }

    @Test
    void elCajeroQueIntentaRestablecerUnaCredencialEsRechazadoYQuedaEnLaBitacora() throws Exception {
        long cajero = idDe("ad.cajero");
        long objetivo = idDe("ad.supervisor");
        String hashAntes = jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE id = ?", String.class, objetivo);
        String cookie = login("/api/auth/login-pin", "ad.cajero", "pin", PIN);

        HttpResponse<String> respuesta = enviar("/api/usuarios/" + objetivo + "/credencial", "POST",
                "{\"credencialTemporal\":\"Robada2026x\"}", cookie);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(ultimo(cajero).get("descripcion"))
                .isEqualTo("Acción sin permiso rechazada: POST /api/usuarios/" + objetivo + "/credencial");
        assertThat(jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE id = ?", String.class, objetivo))
                .isEqualTo(hashAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE descripcion LIKE '%Robada2026x%'", Integer.class)).isZero();
    }

    @Test
    void cadaEndpointProtegidoRechazaYRegistraAlQueNoTienePermiso() throws Exception {
        long cajero = idDe("ad.cajero");
        String cookie = login("/api/auth/login-pin", "ad.cajero", "pin", PIN);
        int antes = denegados(cajero);
        String[][] intentos = {
                {"GET", "/api/usuarios", null},
                {"GET", "/api/usuarios/1", null},
                {"POST", "/api/usuarios", "{\"nombreCompleto\":\"X\",\"username\":\"xx.x\",\"rol\":\"ADMIN\",\"credencialTemporal\":\"Clave2026x\"}"},
                {"PUT", "/api/usuarios/1", "{\"nombreCompleto\":\"X\",\"rol\":\"CAJERO\"}"},
                {"POST", "/api/usuarios/1/desactivar", null},
                {"POST", "/api/usuarios/1/activar", null},
                {"POST", "/api/usuarios/1/credencial", "{\"credencialTemporal\":\"Clave2026x\"}"},
        };

        for (String[] intento : intentos) {
            assertThat(enviar(intento[1], intento[0], intento[2], cookie).statusCode())
                    .as("%s %s", intento[0], intento[1]).isEqualTo(403);
        }
        assertThat(denegados(cajero)).isEqualTo(antes + intentos.length);
    }

    @Test
    void sinSesionSeResponde401YNoHayActorQueRegistrar() throws Exception {
        int antes = jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'ACCESO_DENEGADO'", Integer.class);

        assertThat(enviar("/api/usuarios", "GET", null, null).statusCode()).isEqualTo(401);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = 'ACCESO_DENEGADO'", Integer.class))
                .isEqualTo(antes);
    }

    @Test
    void quienSiTienePermisoNoGeneraRegistrosDeAccesoDenegado() throws Exception {
        long admin = idDe("admin");
        String cookie = login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
        int antes = denegados(admin);

        assertThat(enviar("/api/usuarios", "GET", null, cookie).statusCode()).isEqualTo(200);
        assertThat(denegados(admin)).isEqualTo(antes);
    }

    private int denegados(long usuarioId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE accion = 'ACCESO_DENEGADO' AND usuario_id = ?", Integer.class, usuarioId);
    }

    private Map<String, Object> ultimo(long usuarioId) {
        return jdbc.queryForMap("""
                SELECT rol, exitoso, descripcion FROM bitacora
                WHERE accion = 'ACCESO_DENEGADO' AND usuario_id = ? ORDER BY id DESC LIMIT 1""", usuarioId);
    }

    private long idDe(String username) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private void crearUsuario(String username, int rolId, String credencial) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial));
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        String setCookie = enviar(ruta, "POST", "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null)
                .headers().firstValue("Set-Cookie").orElseThrow();
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
