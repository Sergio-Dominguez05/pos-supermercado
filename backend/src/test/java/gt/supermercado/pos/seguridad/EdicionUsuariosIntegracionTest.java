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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-01.07 Editar usuario, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class EdicionUsuariosIntegracionTest {

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
    void seCambianElNombreCompletoYElRol() throws Exception {
        long id = crearUsuario("ed.nombre", "Nombre Viejo", 2, CONTRASENA);
        String hashAntes = hash(id);

        HttpResponse<String> respuesta = editar(id, "Nombre Nuevo", "ADMIN", null);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"nombreCompleto\":\"Nombre Nuevo\"", "\"rol\":\"ADMIN\"")
                .doesNotContain("argon2", CONTRASENA);
        assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(hash(id)).as("Supervisor y Administrador usan contraseña: se conserva").isEqualTo(hashAntes);
    }

    // Criterio 2

    @Test
    void elNombreDeUsuarioNoSePuedeModificar() throws Exception {
        long id = crearUsuario("ed.fijo", "Usuario Fijo", 2, CONTRASENA);
        String cuerpo = "{\"nombreCompleto\":\"Otro\",\"rol\":\"SUPERVISOR\",\"username\":\"ed.cambiado\"}";

        assertThat(put("/api/usuarios/" + id, cuerpo, cookieAdmin).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT username FROM usuario WHERE id = ?", String.class, id))
                .isEqualTo("ed.fijo");
    }

    // Criterio 3

    @Test
    void dePasarDeCajeroASupervisorSeExigeUnaContrasenaTemporal() throws Exception {
        long id = crearUsuario("ed.cajero", "Cajero Ascendido", 3, PIN);

        HttpResponse<String> sinCredencial = editar(id, "Cajero Ascendido", "SUPERVISOR", null);
        HttpResponse<String> conPin = editar(id, "Cajero Ascendido", "SUPERVISOR", "1234");
        assertThat(sinCredencial.statusCode()).isEqualTo(422);
        assertThat(sinCredencial.body()).contains("CONTRASENA_INVALIDA");
        assertThat(conPin.statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", Integer.class, id)).isEqualTo(3);

        assertThat(editar(id, "Cajero Ascendido", "SUPERVISOR", "Temporal2026").statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT credencial_temporal FROM usuario WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(post("/api/auth/login", "{\"username\":\"ed.cajero\",\"contrasena\":\"Temporal2026\"}", null)
                .statusCode()).isEqualTo(200);
        assertThat(post("/api/auth/login-pin", "{\"username\":\"ed.cajero\",\"pin\":\"" + PIN + "\"}", null)
                .statusCode()).isEqualTo(401);
    }

    @Test
    void dePasarDeSupervisorACajeroSeExigeUnPinTemporal() throws Exception {
        long id = crearUsuario("ed.super", "Supervisor a Caja", 2, CONTRASENA);

        HttpResponse<String> conContrasena = editar(id, "Supervisor a Caja", "CAJERO", "Temporal2026");
        assertThat(conContrasena.statusCode()).isEqualTo(422);
        assertThat(conContrasena.body()).contains("PIN_INVALIDO");

        assertThat(editar(id, "Supervisor a Caja", "CAJERO", "5821").statusCode()).isEqualTo(200);
        assertThat(post("/api/auth/login-pin", "{\"username\":\"ed.super\",\"pin\":\"5821\"}", null).statusCode())
                .isEqualTo(200);
    }

    // Criterio 4

    @Test
    void laSesionAbiertaConservaSuRolYPermisosHastaElSiguienteInicioDeSesion() throws Exception {
        long id = crearUsuario("ed.sesion", "Cajero con Sesión", 3, PIN);
        String cookieCajero = login("/api/auth/login-pin", "ed.sesion", "pin", PIN);

        assertThat(editar(id, "Cajero con Sesión", "SUPERVISOR", "Temporal2026").statusCode()).isEqualTo(200);

        String sesionAnterior = get("/api/auth/sesion", cookieCajero).body();
        assertThat(sesionAnterior).contains("\"rol\":\"CAJERO\"").doesNotContain("REPORTE_CONSULTAR");

        String cookieNueva = login("/api/auth/login", "ed.sesion", "contrasena", "Temporal2026");
        assertThat(get("/api/auth/sesion", cookieNueva).body()).contains("\"rol\":\"SUPERVISOR\"", "REPORTE_CONSULTAR");
    }

    // Criterio 5

    @Test
    void cadaCambioQuedaEnLaBitacoraConElValorAnteriorYElNuevo() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        long id = crearUsuario("ed.bitacora", "Antes López", 3, PIN);

        editar(id, "Después López", "SUPERVISOR", "Temporal2026");

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT usuario_id, exitoso,
                       cambios->'nombreCompleto'->>'antes' AS nombre_antes,
                       cambios->'nombreCompleto'->>'despues' AS nombre_despues,
                       cambios->'rol'->>'antes' AS rol_antes, cambios->'rol'->>'despues' AS rol_despues,
                       cambios->>'credencial' AS credencial, cambios::text AS todo
                FROM bitacora WHERE accion = 'USUARIO_EDITADO' AND entidad_id = ? ORDER BY id DESC LIMIT 1""", id);
        assertThat(registro.get("usuario_id")).isEqualTo(admin);
        assertThat(registro.get("exitoso")).isEqualTo(true);
        assertThat(registro.get("nombre_antes")).isEqualTo("Antes López");
        assertThat(registro.get("nombre_despues")).isEqualTo("Después López");
        assertThat(registro.get("rol_antes")).isEqualTo("CAJERO");
        assertThat(registro.get("rol_despues")).isEqualTo("SUPERVISOR");
        assertThat(registro.get("credencial")).isEqualTo("modificado");
        assertThat((String) registro.get("todo")).doesNotContain("Temporal2026", PIN);
    }

    @Test
    void siNadaCambiaNoSeRegistraNada() throws Exception {
        long id = crearUsuario("ed.igual", "Sin Cambios", 2, CONTRASENA);

        assertThat(editar(id, "  Sin Cambios ", "SUPERVISOR", null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bitacora WHERE accion = 'USUARIO_EDITADO' AND entidad_id = ?", Integer.class, id))
                .isZero();
    }

    // Protección del último Administrador (HU-01.09 crit. 3)

    @Test
    void noSePuedeQuitarElRolAlUltimoAdministradorActivo() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        List<Long> otrosActivos = jdbc.queryForList(
                "SELECT id FROM usuario WHERE rol_id = 1 AND activo AND id <> ?", Long.class, admin);
        jdbc.update("UPDATE usuario SET activo = false WHERE rol_id = 1 AND id <> ?", admin);
        try {
            HttpResponse<String> respuesta = editar(admin, "Administrador", "SUPERVISOR", null);

            assertThat(respuesta.statusCode()).isEqualTo(422);
            assertThat(respuesta.body()).contains("ULTIMO_ADMINISTRADOR");
            assertThat(jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", Integer.class, admin))
                    .isEqualTo(1);
        } finally {
            for (Long otro : otrosActivos) {
                jdbc.update("UPDATE usuario SET activo = true WHERE id = ?", otro);
            }
        }
    }

    @Test
    void siHayOtroAdministradorActivoSiSePuedeCambiarElRol() throws Exception {
        long id = crearUsuario("ed.admin2", "Segundo Admin", 1, CONTRASENA);

        assertThat(editar(id, "Segundo Admin", "SUPERVISOR", null).statusCode()).isEqualTo(200);
    }

    // Consulta, datos inválidos y permisos

    @Test
    void seObtieneUnUsuarioParaEditarloSinSuCredencial() throws Exception {
        long id = crearUsuario("ed.consulta", "Para Consultar", 3, PIN);

        HttpResponse<String> respuesta = get("/api/usuarios/" + id, cookieAdmin);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"username\":\"ed.consulta\"", "\"rol\":\"CAJERO\"")
                .doesNotContain("argon2", "credencial");
    }

    @Test
    void unUsuarioQueNoExisteDa404YUnIdInvalido400() throws Exception {
        assertThat(get("/api/usuarios/999999", cookieAdmin).statusCode()).isEqualTo(404);
        assertThat(editar(999999, "Nadie", "CAJERO", PIN).statusCode()).isEqualTo(404);
        assertThat(get("/api/usuarios/abc", cookieAdmin).statusCode()).isEqualTo(400);
        assertThat(put("/api/usuarios/1", "{\"nombreCompleto\":\"\",\"rol\":\"CAJERO\"}", cookieAdmin).statusCode())
                .isEqualTo(400);
    }

    @Test
    void soloQuienTienePermisoDeAdministrarUsuariosPuedeEditarlos() throws Exception {
        long id = crearUsuario("ed.objetivo", "Objetivo", 3, PIN);
        crearUsuario("ed.vigilante", "Vigilante", 2, CONTRASENA);
        String cookieSupervisor = login("/api/auth/login", "ed.vigilante", "contrasena", CONTRASENA);
        String cuerpo = "{\"nombreCompleto\":\"Hackeado\",\"rol\":\"ADMIN\",\"credencialTemporal\":\"Temporal2026\"}";

        assertThat(put("/api/usuarios/" + id, cuerpo, cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(get("/api/usuarios/" + id, cookieSupervisor).statusCode()).isEqualTo(403);
        assertThat(put("/api/usuarios/" + id, cuerpo, null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT nombre_completo FROM usuario WHERE id = ?", String.class, id))
                .isEqualTo("Objetivo");
    }

    private HttpResponse<String> editar(long id, String nombre, String rol, String credencial) throws Exception {
        String cuerpo = credencial == null
                ? "{\"nombreCompleto\":\"%s\",\"rol\":\"%s\"}".formatted(nombre, rol)
                : "{\"nombreCompleto\":\"%s\",\"rol\":\"%s\",\"credencialTemporal\":\"%s\"}".formatted(nombre, rol, credencial);
        return put("/api/usuarios/" + id, cuerpo, cookieAdmin);
    }

    private long crearUsuario(String username, String nombre, int rolId, String credencial) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false) ON CONFLICT (username) DO NOTHING""",
                username, nombre, rolId, codificador.encode(credencial));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String hash(long id) {
        return jdbc.queryForObject("SELECT credencial_hash FROM usuario WHERE id = ?", String.class, id);
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        HttpResponse<String> respuesta = post(ruta,
                "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null);
        String setCookie = respuesta.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> post(String ruta, String cuerpo, String cookie) throws Exception {
        return enviar(ruta, "POST", cuerpo, cookie);
    }

    private HttpResponse<String> put(String ruta, String cuerpo, String cookie) throws Exception {
        return enviar(ruta, "PUT", cuerpo, cookie);
    }

    private HttpResponse<String> get(String ruta, String cookie) throws Exception {
        return enviar(ruta, "GET", null, cookie);
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
