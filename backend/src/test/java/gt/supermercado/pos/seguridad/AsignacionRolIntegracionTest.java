package gt.supermercado.pos.seguridad;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-01.09 Asignar rol a un usuario. El rol se asigna al crear (HU-01.05) y al editar (HU-01.07);
 * estas pruebas demuestran cada criterio de la historia contra PostgreSQL real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class AsignacionRolIntegracionTest {

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
        cookieAdmin = loginContrasena("admin", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
    }

    // Criterio 1: cada usuario tiene exactamente un rol (Administrador, Supervisor o Cajero)

    @Test
    void soloExistenLosRolesAdministradorSupervisorYCajero() {
        assertThat(jdbc.queryForList("SELECT codigo FROM rol", String.class))
                .containsExactlyInAnyOrder("ADMIN", "SUPERVISOR", "CAJERO");
    }

    @Test
    void laBaseDeDatosNoAdmiteUnUsuarioSinRolNiConUnRolInexistente() {
        String hash = codificador.encode(CONTRASENA);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash)
                VALUES ('ar.sinrol', 'Sin rol', NULL, ?)""", hash))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash)
                VALUES ('ar.rolfalso', 'Rol falso', 9, ?)""", hash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laApiNoAceptaUnRolDistintoDeLosTres() throws Exception {
        long id = crearUsuario("ar.gerente", 2, CONTRASENA);

        assertThat(editar(id, "GERENTE", null, cookieAdmin).statusCode()).isEqualTo(400);
        assertThat(rolDe(id)).isEqualTo(2);
    }

    // Criterio 2: al asignar un rol, el usuario obtiene los permisos definidos para ese rol

    @Test
    void cadaRolRecibeExactamenteLosPermisosDeSuMatriz() throws Exception {
        crearUsuario("ar.matriz.admin", 1, CONTRASENA);
        crearUsuario("ar.matriz.super", 2, CONTRASENA);
        crearUsuario("ar.matriz.cajero", 3, PIN);

        assertThat(permisos(loginContrasena("ar.matriz.admin", CONTRASENA))).isEqualTo(matriz(1));
        assertThat(permisos(loginContrasena("ar.matriz.super", CONTRASENA))).isEqualTo(matriz(2));
        assertThat(permisos(loginPin("ar.matriz.cajero", PIN))).isEqualTo(matriz(3));
    }

    @Test
    void alCambiarleElRolObtieneLosPermisosDelNuevoRol() throws Exception {
        long id = crearUsuario("ar.ascenso", 3, PIN);
        assertThat(permisos(loginPin("ar.ascenso", PIN))).isEqualTo(matriz(3));

        assertThat(editar(id, "SUPERVISOR", "Temporal2026", cookieAdmin).statusCode()).isEqualTo(200);

        List<String> nuevos = permisos(loginContrasena("ar.ascenso", "Temporal2026"));
        assertThat(nuevos).isEqualTo(matriz(2)).contains("REPORTE_CONSULTAR", "FACTURA_ANULAR")
                .doesNotContain("USUARIO_ADMINISTRAR");
    }

    // Criterio 3: no puedo quitarme el rol de Administrador si soy el último administrador activo

    @Test
    void elUltimoAdministradorActivoNoPuedeQuitarseElRol() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        List<Long> otrosActivos = jdbc.queryForList(
                "SELECT id FROM usuario WHERE rol_id = 1 AND activo AND id <> ?", Long.class, admin);
        jdbc.update("UPDATE usuario SET activo = false WHERE rol_id = 1 AND id <> ?", admin);
        try {
            HttpResponse<String> respuesta = editar(admin, "SUPERVISOR", null, cookieAdmin);

            assertThat(respuesta.statusCode()).isEqualTo(422);
            assertThat(respuesta.body()).contains("ULTIMO_ADMINISTRADOR",
                    "No se puede quitar el rol al último Administrador activo.");
            assertThat(rolDe(admin)).isEqualTo(1);
        } finally {
            for (Long otro : otrosActivos) {
                jdbc.update("UPDATE usuario SET activo = true WHERE id = ?", otro);
            }
        }
    }

    @Test
    void siHayOtroAdministradorActivoSiPuedeQuitarseElRolASiMismo() throws Exception {
        long id = crearUsuario("ar.admin.saliente", 1, CONTRASENA);
        String cookiePropia = loginContrasena("ar.admin.saliente", CONTRASENA);

        assertThat(editar(id, "SUPERVISOR", null, cookiePropia).statusCode()).isEqualTo(200);

        assertThat(rolDe(id)).isEqualTo(2);
        assertThat(permisos(loginContrasena("ar.admin.saliente", CONTRASENA))).isEqualTo(matriz(2));
    }

    // Criterio 4: el cambio de rol queda registrado en la bitácora

    @Test
    void elCambioDeRolQuedaEnLaBitacoraConQuienLoHizo() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        long id = crearUsuario("ar.bitacora", 2, CONTRASENA);

        editar(id, "ADMIN", null, cookieAdmin);

        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT usuario_id, rol, exitoso, cambios->'rol'->>'antes' AS antes, cambios->'rol'->>'despues' AS despues
                FROM bitacora WHERE accion = 'USUARIO_EDITADO' AND entidad_id = ? ORDER BY id DESC LIMIT 1""", id);
        assertThat(registro.get("usuario_id")).isEqualTo(admin);
        assertThat(registro.get("rol")).isEqualTo("ADMIN");
        assertThat(registro.get("exitoso")).isEqualTo(true);
        assertThat(registro.get("antes")).isEqualTo("SUPERVISOR");
        assertThat(registro.get("despues")).isEqualTo("ADMIN");
    }

    @Test
    void elIntentoRechazadoDeQuitarseElRolTambienQuedaEnLaBitacora() throws Exception {
        long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        List<Long> otrosActivos = jdbc.queryForList(
                "SELECT id FROM usuario WHERE rol_id = 1 AND activo AND id <> ?", Long.class, admin);
        jdbc.update("UPDATE usuario SET activo = false WHERE rol_id = 1 AND id <> ?", admin);
        try {
            int antes = rechazos(admin);

            editar(admin, "CAJERO", PIN, cookieAdmin);

            assertThat(rechazos(admin)).isEqualTo(antes + 1);
        } finally {
            for (Long otro : otrosActivos) {
                jdbc.update("UPDATE usuario SET activo = true WHERE id = ?", otro);
            }
        }
    }

    private int rechazos(long admin) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM bitacora
                WHERE accion = 'USUARIO_EDITADO' AND NOT exitoso AND entidad_id = ?""", Integer.class, admin);
    }

    /** Permisos que la base de datos define para el rol, en orden. */
    private List<String> matriz(int rolId) {
        return jdbc.queryForList("SELECT permiso FROM rol_permiso WHERE rol_id = ? ORDER BY permiso", String.class, rolId);
    }

    /** Permisos de la sesión, tal como los devuelve {@code GET /api/auth/sesion} (ya vienen ordenados). */
    private List<String> permisos(String cookie) throws Exception {
        String cuerpo = enviar("/api/auth/sesion", "GET", null, cookie).body();
        String lista = cuerpo.substring(cuerpo.indexOf("\"permisos\":[") + 12, cuerpo.indexOf(']', cuerpo.indexOf("\"permisos\":[")));
        return lista.isEmpty() ? List.of() : Arrays.stream(lista.split(",")).map(p -> p.replace("\"", "")).toList();
    }

    private int rolDe(long id) {
        return jdbc.queryForObject("SELECT rol_id FROM usuario WHERE id = ?", Integer.class, id);
    }

    private HttpResponse<String> editar(long id, String rol, String credencial, String cookie) throws Exception {
        String nombre = jdbc.queryForObject("SELECT nombre_completo FROM usuario WHERE id = ?", String.class, id);
        String cuerpo = credencial == null
                ? "{\"nombreCompleto\":\"%s\",\"rol\":\"%s\"}".formatted(nombre, rol)
                : "{\"nombreCompleto\":\"%s\",\"rol\":\"%s\",\"credencialTemporal\":\"%s\"}".formatted(nombre, rol, credencial);
        return enviar("/api/usuarios/" + id, "PUT", cuerpo, cookie);
    }

    private long crearUsuario(String username, int rolId, String credencial) {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false) ON CONFLICT (username) DO NOTHING""",
                username, "Usuario " + username, rolId, codificador.encode(credencial));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE username = ?", Long.class, username);
    }

    private String loginContrasena(String username, String contrasena) throws Exception {
        return cookie(enviar("/api/auth/login", "POST",
                "{\"username\":\"%s\",\"contrasena\":\"%s\"}".formatted(username, contrasena), null));
    }

    private String loginPin(String username, String pin) throws Exception {
        return cookie(enviar("/api/auth/login-pin", "POST",
                "{\"username\":\"%s\",\"pin\":\"%s\"}".formatted(username, pin), null));
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
