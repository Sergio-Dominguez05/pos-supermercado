package gt.supermercado.pos.bitacora;

import gt.supermercado.pos.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-11.05 Proteger la bitácora contra cambios, contra PostgreSQL real con las migraciones aplicadas. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pos.seguridad.admin-inicial.contrasena=" + BitacoraInmutableIntegracionTest.CONTRASENA_ADMIN)
@ActiveProfiles("test")
class BitacoraInmutableIntegracionTest {

    /** La misma que usan las demás pruebas de integración, para compartir el contexto de Spring y el contenedor. */
    static final String CONTRASENA_ADMIN = "Inicial2026";
    static final String CONTRASENA = "Clave2026segura";
    static final String PIN = "4821";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder codificador;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    @Value("${local.server.port}")
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void hayRegistros() throws Exception {
        jdbc.update("""
                INSERT INTO usuario (username, nombre_completo, rol_id, credencial_hash, credencial_temporal)
                VALUES ('bi.supervisor', 'Supervisor bitácora', 2, ?, false), ('bi.cajero', 'Cajero bitácora', 3, ?, false)
                ON CONFLICT (username) DO NOTHING""", codificador.encode(CONTRASENA), codificador.encode(PIN));
        login("bi.supervisor", "contrasena", CONTRASENA);
    }

    // Criterio 1: ninguna opción para editar ni borrar

    @Test
    void laApiDeLaBitacoraSoloTieneLecturas() {
        List<RequestMappingInfo> deBitacora = rutas.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().stream().anyMatch(p -> p.startsWith("/api/bitacora")))
                .toList();

        assertThat(deBitacora).isNotEmpty().allSatisfy(info ->
                assertThat(info.getMethodsCondition().getMethods()).extracting(Enum::name).containsOnly("GET"));
    }

    // Criterio 2: la base de datos impide modificar o borrar, a cualquier rol

    @Test
    void niSiquieraElDuenoPuedeModificarBorrarNiVaciarLaBitacora() {
        // El mensaje del trigger viene en la causa: Spring envuelve el error de PostgreSQL.
        assertThatThrownBy(() -> jdbc.update("UPDATE bitacora SET descripcion = 'cambiada' WHERE secuencia = 1"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM bitacora WHERE secuencia = 1"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE bitacora CASCADE"))
                .rootCause().hasMessageContaining("La bitácora no se puede modificar ni borrar");
    }

    @Test
    void laAplicacionSoloPuedeLeerYAgregarYNoTocaLaCabezaDeLaCadena() {
        assertThat(privilegiosDeLaAplicacion("bitacora")).containsExactlyInAnyOrder("SELECT", "INSERT");
        assertThat(privilegiosDeLaAplicacion("bitacora_cadena")).isEmpty();
    }

    @Test
    void conElRolDeLaAplicacionSeAgreganRegistrosEncadenadosPeroNoSeTocaLaCabeza() {
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            try (Statement sql = conexion.createStatement()) {
                sql.execute("SET ROLE pos_app");
                sql.execute("""
                        INSERT INTO bitacora (modulo, accion, exitoso, descripcion)
                        VALUES ('pruebas', 'ROL_APLICACION', true, 'Insertado con el rol de la aplicación')""");
                assertThatThrownBy(() -> sql.execute("UPDATE bitacora_cadena SET ultima_secuencia = 0"))
                        .hasMessageContaining("permission denied");
            } finally {
                try (Statement sql = conexion.createStatement()) {
                    sql.execute("RESET ROLE");
                }
            }
            return null;
        });

        assertThat(problemas()).isEmpty();
    }

    // Criterio 3: se puede verificar que no falten registros ni hayan sido alterados

    @Test
    void cadaRegistroSeEncadenaConElAnteriorConSecuenciaConsecutiva() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT secuencia, hash_anterior, lag(secuencia) OVER w AS previa, lag(hash) OVER w AS hash_previo
                    FROM bitacora WINDOW w AS (ORDER BY secuencia)) x
                WHERE x.previa IS NOT NULL AND (x.secuencia <> x.previa + 1 OR x.hash_anterior <> x.hash_previo)""",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT min(secuencia) FROM bitacora", Long.class)).isEqualTo(1);
        assertThat(problemas()).isEmpty();
    }

    @Test
    void laVerificacionDetectaUnRegistroAlterado() {
        long secuencia = jdbc.queryForObject("SELECT max(secuencia) - 1 FROM bitacora", Long.class);
        String original = jdbc.queryForObject("SELECT descripcion FROM bitacora WHERE secuencia = ?", String.class, secuencia);

        sinProteccion("UPDATE bitacora SET descripcion = 'Texto cambiado a escondidas' WHERE secuencia = " + secuencia);
        try {
            assertThat(problemas()).containsExactly(
                    secuencia + ": El registro fue alterado: su contenido no coincide con su huella.");
        } finally {
            sinProteccion("UPDATE bitacora SET descripcion = '%s' WHERE secuencia = %d"
                    .formatted(original.replace("'", "''"), secuencia));
        }
        assertThat(problemas()).isEmpty();
    }

    @Test
    void laVerificacionDetectaUnRegistroBorrado() {
        long secuencia = jdbc.queryForObject("SELECT max(secuencia) - 1 FROM bitacora", Long.class);

        List<String> encontrados = jdbc.execute((ConnectionCallback<List<String>>) conexion -> {
            try (Statement sql = conexion.createStatement()) {
                sql.execute("CREATE TEMP TABLE copia_bitacora AS SELECT * FROM bitacora WHERE secuencia = " + secuencia);
                sql.execute("ALTER TABLE bitacora DISABLE TRIGGER bitacora_sin_cambios");
                sql.execute("DELETE FROM bitacora WHERE secuencia = " + secuencia);
                sql.execute("ALTER TABLE bitacora ENABLE TRIGGER bitacora_sin_cambios");
                List<String> problemas = leerProblemas(conexion);

                sql.execute("ALTER TABLE bitacora DISABLE TRIGGER bitacora_encadenar");
                sql.execute("INSERT INTO bitacora OVERRIDING SYSTEM VALUE SELECT * FROM copia_bitacora");
                sql.execute("ALTER TABLE bitacora ENABLE TRIGGER bitacora_encadenar");
                sql.execute("DROP TABLE copia_bitacora");
                return problemas;
            }
        });

        assertThat(encontrados).contains(secuencia + 1 + ": Faltan los registros %d a %d.".formatted(secuencia, secuencia));
        assertThat(problemas()).isEmpty();
    }

    @Test
    void losRegistrosConcurrentesQuedanBienEncadenados() throws Exception {
        long antes = jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> intentos = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                intentos.add(hilos.submit(() -> login("bi.cajero", "pin", "0000").statusCode()));
            }
            for (Future<Integer> intento : intentos) {
                assertThat(intento.get()).isEqualTo(401);
            }
        } finally {
            hilos.shutdown();
        }

        assertThat(jdbc.queryForObject("SELECT max(secuencia) FROM bitacora", Long.class)).isEqualTo(antes + 16);
        assertThat(problemas()).isEmpty();
    }

    @Test
    void laVerificacionSeConsultaDesdeLaApiConElPermisoDeBitacora() throws Exception {
        String supervisor = cookie(login("bi.supervisor", "contrasena", CONTRASENA));
        String cajero = cookie(login("bi.cajero", "pin", PIN));

        HttpResponse<String> respuesta = get("/api/bitacora/verificacion", supervisor);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"integra\":true", "\"problemas\":[]").containsPattern("\"registros\":[1-9]");
        assertThat(get("/api/bitacora/verificacion", cajero).statusCode()).isEqualTo(403);
        assertThat(get("/api/bitacora/verificacion", null).statusCode()).isEqualTo(401);
    }

    private List<String> problemas() {
        return jdbc.execute((ConnectionCallback<List<String>>) BitacoraInmutableIntegracionTest::leerProblemas);
    }

    private static List<String> leerProblemas(Connection conexion) throws java.sql.SQLException {
        List<String> problemas = new ArrayList<>();
        try (Statement sql = conexion.createStatement();
             ResultSet filas = sql.executeQuery("SELECT secuencia, problema FROM verificar_bitacora() ORDER BY secuencia")) {
            while (filas.next()) {
                problemas.add(filas.getLong(1) + ": " + filas.getString(2));
            }
        }
        return problemas;
    }

    /** Lo que haría alguien con acceso de superusuario: quitar la protección, cambiar y volver a ponerla. */
    private void sinProteccion(String sentencia) {
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            try (Statement sql = conexion.createStatement()) {
                sql.execute("ALTER TABLE bitacora DISABLE TRIGGER bitacora_sin_cambios");
                sql.execute(sentencia);
                sql.execute("ALTER TABLE bitacora ENABLE TRIGGER bitacora_sin_cambios");
            }
            return null;
        });
    }

    private List<String> privilegiosDeLaAplicacion(String tabla) {
        return jdbc.queryForList("""
                SELECT privilege_type FROM information_schema.role_table_grants
                WHERE grantee = 'pos_app' AND table_name = ?""", String.class, tabla);
    }

    private HttpResponse<String> login(String username, String campo, String valor) throws Exception {
        String ruta = "pin".equals(campo) ? "/api/auth/login-pin" : "/api/auth/login";
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"%s\",\"%s\":\"%s\"}".formatted(username, campo, valor)))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<String> login) {
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> get(String ruta, String cookie) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
