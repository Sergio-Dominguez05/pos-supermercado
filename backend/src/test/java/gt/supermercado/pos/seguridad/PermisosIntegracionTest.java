package gt.supermercado.pos.seguridad;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-01.10 Configurar permisos por rol, contra PostgreSQL real. La matriz es compartida por todas las pruebas: cada
 * prueba que la cambia la deja como estaba (la configuración inicial de V2).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + InicioSesionIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class PermisosIntegracionTest {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    JsonMapper json;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private String admin;
    private long adminId;
    private List<String> cajeroInicial;
    private List<String> supervisorInicial;

    @BeforeEach
    void preparar() throws Exception {
        admin = login("/api/auth/login", "admin", "contrasena", InicioSesionIntegracionTest.CONTRASENA_ADMIN);
        adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'admin'", Long.class);
        cajeroInicial = delRol(3);
        supervisorInicial = delRol(2);
    }

    @AfterEach
    void restaurar() {
        restaurar(2, supervisorInicial);
        restaurar(3, cajeroInicial);
    }

    @Test
    void laMatrizAgrupaLosPermisosPorModuloConLaConfiguracionInicial() throws Exception {   // crit. 1 y 5
        JsonNode matriz = ok(enviar("/api/permisos", "GET", null, admin));

        List<String> modulos = new ArrayList<>();
        matriz.get("modulos").forEach(m -> modulos.add(m.get("nombre").asString()));
        assertThat(modulos).startsWith("Catálogo de productos", "Ventas").contains("Facturación", "Caja", "Reportes",
                "Configuración", "Usuarios");
        JsonNode reportes = permiso(matriz, "REPORTE_CONSULTAR");
        assertThat(reportes.get("descripcion").asString()).isEqualTo("Consultar reportes");
        assertThat(reportes.get("supervisor").asBoolean()).isTrue();
        assertThat(reportes.get("cajero").asBoolean()).isFalse();
        assertThat(reportes.get("exclusivoAdmin").asBoolean()).isFalse();
        for (String exclusivo : List.of("PRODUCTO_EDITAR", "CREDENCIAL_RESTABLECER", "USUARIO_ADMINISTRAR",
                "PERMISO_CONFIGURAR")) {
            assertThat(permiso(matriz, exclusivo).get("exclusivoAdmin").asBoolean()).as(exclusivo).isTrue();
        }
        List<String> roles = new ArrayList<>();
        matriz.get("rolesConfigurables").forEach(r -> roles.add(r.asString()));
        assertThat(roles).containsExactly("SUPERVISOR", "CAJERO");
    }

    @Test
    void elAdministradorCambiaLosPermisosDelCajeroYSeAplicanEnElSiguienteInicio() throws Exception {   // crit. 2 y 6
        String cajero = usuario(3, "4821");
        String sesionAbierta = login("/api/auth/login-pin", cajero, "pin", "4821");
        assertThat(enviar("/api/reportes/filtros", "GET", null, sesionAbierta).statusCode()).isEqualTo(403);

        List<String> nuevos = new ArrayList<>(cajeroInicial);
        nuevos.add("REPORTE_CONSULTAR");
        nuevos.remove("CLIENTE_GESTIONAR");
        JsonNode matriz = ok(enviar("/api/roles/CAJERO/permisos", "PUT", cuerpo(nuevos), admin));

        assertThat(permiso(matriz, "REPORTE_CONSULTAR").get("cajero").asBoolean()).isTrue();
        assertThat(permiso(matriz, "CLIENTE_GESTIONAR").get("cajero").asBoolean()).isFalse();
        assertThat(delRol(3)).contains("REPORTE_CONSULTAR").doesNotContain("CLIENTE_GESTIONAR");
        // Decisión de Sergio: la sesión abierta conserva sus permisos; el siguiente inicio de sesión ya los trae.
        assertThat(enviar("/api/reportes/filtros", "GET", null, sesionAbierta).statusCode()).isEqualTo(403);
        String nuevaSesion = login("/api/auth/login-pin", cajero, "pin", "4821");
        assertThat(enviar("/api/reportes/filtros", "GET", null, nuevaSesion).statusCode()).isEqualTo(200);

        var registro = jdbc.queryForMap("""
                SELECT descripcion, cambios->'REPORTE_CONSULTAR'->>'antes' AS antes,
                       cambios->'REPORTE_CONSULTAR'->>'despues' AS despues,
                       cambios->'CLIENTE_GESTIONAR'->>'despues' AS cliente
                FROM bitacora WHERE accion = 'PERMISOS_MODIFICADOS' AND exitoso AND usuario_id = ?
                ORDER BY secuencia DESC LIMIT 1""", adminId);
        assertThat((String) registro.get("descripcion")).isEqualTo(
                "Permisos del rol CAJERO modificados: -CLIENTE_GESTIONAR, +REPORTE_CONSULTAR");
        assertThat(registro.get("antes")).isEqualTo("false");
        assertThat(registro.get("despues")).isEqualTo("true");
        assertThat(registro.get("cliente")).isEqualTo("false");
    }

    @Test
    void losPermisosDelAdministradorNoSeReducenYLosExclusivosNoSeAsignan() throws Exception {   // crit. 3 y 4
        assertError(enviar("/api/roles/ADMIN/permisos", "PUT", cuerpo(List.of("VENTA_REGISTRAR")), admin),
                "ROL_NO_CONFIGURABLE", "Los permisos del Administrador no se pueden reducir.");
        assertError(enviar("/api/roles/SISTEMA/permisos", "PUT", cuerpo(List.of("VENTA_REGISTRAR")), admin),
                "ROL_NO_CONFIGURABLE", "Solo se configuran los permisos del Supervisor y del Cajero.");
        List<String> conExclusivo = new ArrayList<>(supervisorInicial);
        conExclusivo.add("PRODUCTO_EDITAR");
        assertError(enviar("/api/roles/SUPERVISOR/permisos", "PUT", cuerpo(conExclusivo), admin),
                "PERMISO_EXCLUSIVO_ADMIN", "Crear productos y cambiar precios");
        assertError(enviar("/api/roles/SUPERVISOR/permisos", "PUT", cuerpo(List.of("NO_EXISTE")), admin),
                "PERMISO_INEXISTENTE", "El permiso NO_EXISTE no existe.");
        assertThat(delRol(2)).containsExactlyInAnyOrderElementsOf(supervisorInicial);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bitacora WHERE accion = 'PERMISOS_MODIFICADOS' AND NOT exitoso
                AND usuario_id = ? AND descripcion LIKE 'Cambio de permisos del rol%'""", Integer.class, adminId))
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void laBaseDeDatosTambienImpideRomperLasReglas() {   // crit. 3 y 4, defensa en profundidad (V33)
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (2, 'PRODUCTO_EDITAR')"))
                .hasMessageContaining("exclusivo del Administrador");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM rol_permiso WHERE rol_id = 1 AND permiso = 'VENTA_REGISTRAR'"))
                .hasMessageContaining("Administrador no se pueden reducir");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (4, 'VENTA_REGISTRAR')"))
                .hasMessageContaining("Sistema no tiene permisos");
    }

    @Test
    void soloQuienConfiguraPermisosVeYCambiaLaMatriz() throws Exception {
        String supervisor = usuario(2, "Clave2026segura");
        String sesion = login("/api/auth/login", supervisor, "contrasena", "Clave2026segura");

        assertThat(enviar("/api/permisos", "GET", null, sesion).statusCode()).isEqualTo(403);
        assertThat(enviar("/api/roles/CAJERO/permisos", "PUT", cuerpo(List.of()), sesion).statusCode())
                .isEqualTo(403);
        assertThat(delRol(3)).containsExactlyInAnyOrderElementsOf(cajeroInicial);
    }

    // Apoyo

    private static JsonNode permiso(JsonNode matriz, String codigo) {
        for (JsonNode modulo : matriz.get("modulos")) {
            for (JsonNode permiso : modulo.get("permisos")) {
                if (permiso.get("codigo").asString().equals(codigo)) {
                    return permiso;
                }
            }
        }
        throw new AssertionError("No está el permiso " + codigo);
    }

    private List<String> delRol(int rolId) {
        return jdbc.queryForList("SELECT permiso FROM rol_permiso WHERE rol_id = ? ORDER BY permiso", String.class,
                rolId);
    }

    private void restaurar(int rolId, List<String> permisos) {
        jdbc.update("DELETE FROM rol_permiso WHERE rol_id = ?", rolId);
        permisos.forEach(p -> jdbc.update("INSERT INTO rol_permiso (rol_id, permiso) VALUES (?, ?)", rolId, p));
    }

    private String cuerpo(List<String> permisos) {
        return "{\"permisos\":[" + String.join(",", permisos.stream().map(p -> "\"" + p + "\"").toList()) + "]}";
    }

    private void assertError(HttpResponse<String> respuesta, String codigo, String mensaje) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(422);
        assertThat(respuesta.body()).contains(codigo, mensaje);
    }

    private JsonNode ok(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private String usuario(int rolId, String credencial) {
        String username = "pr.u" + SIGUIENTE.incrementAndGet() + "x" + System.nanoTime() % 1_000_000;
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES (?, ?, ?, ?, false)""", username, "Usuario " + username, rolId, codificador.encode(credencial));
        return username;
    }

    private String login(String ruta, String username, String campo, String valor) throws Exception {
        HttpResponse<String> respuesta = enviar(ruta, "POST",
                "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor), null);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        String setCookie = respuesta.headers().firstValue("Set-Cookie").orElseThrow();
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
